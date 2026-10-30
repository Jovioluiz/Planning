/**
 * Descrição dos chamados do Redmine, que vem em Textile (h2., *negrito*, # listas, |tabelas|), em HTML para o
 * painel de detalhe. Quem mostra usa [innerHTML], que o Angular sanitiza (scripts e eventos não passam).
 * A biblioteca é carregada sob demanda: só o painel de uma tarefa do Redmine a usa.
 *
 * @param carregarAnexo endereço (blob:) da imagem anexada ao chamado com esse nome; nulo se não deu para baixar
 */
export async function textileParaHtml(texto: string,
                                      carregarAnexo: (nome: string) => Promise<string | null>): Promise<string> {
  const textile = (await import('textile-js')).default;
  return ajustarLinksEImagens(textile.convert(semMacros(texto)), carregarAnexo);
}

/** {{collapse(Título) ... }} vira o título em negrito seguido do conteúdo; {{toc}} e as demais macros somem. */
function semMacros(texto: string): string {
  return texto
    .replace(/\{\{collapse\(([^)]*)\)/g, (_, titulo: string) => titulo.trim() ? `\n\n*${titulo.trim()}*\n\n` : '\n\n')
    .replace(/\{\{collapse/g, '\n\n')
    .replace(/^[ \t]*\}\}[ \t]*$/gm, '')
    .replace(/\{\{[^}]*\}\}/g, '');
}

/**
 * Imagens anexadas ao chamado (!print.png!) vêm só com o nome do arquivo: são baixadas pelo backend (o Redmine
 * exige a chave); a que falhar vira um aviso. Links relativos (páginas do Redmine) viram texto; os absolutos
 * abrem em outra aba.
 */
async function ajustarLinksEImagens(html: string,
                                    carregarAnexo: (nome: string) => Promise<string | null>): Promise<string> {
  if (typeof DOMParser === 'undefined') return html;
  const doc = new DOMParser().parseFromString(html, 'text/html');
  const absoluto = (url: string | null) => /^https?:\/\//i.test(url ?? '');
  await Promise.all(Array.from(doc.querySelectorAll('img')).map(async img => {
    const src = img.getAttribute('src') ?? '';
    if (absoluto(src)) return;
    const nome = nomeDoAnexo(src);
    const url = nome ? await carregarAnexo(nome).catch(() => null) : null;
    if (url) {
      img.setAttribute('src', url);
      img.setAttribute('alt', img.getAttribute('alt') || nome);
      img.setAttribute('title', img.getAttribute('title') || nome);
      return;
    }
    const aviso = doc.createElement('span');
    aviso.className = 'textile-anexo';
    aviso.textContent = `[imagem do Redmine não carregada: ${nome || 'sem nome'}]`;
    img.replaceWith(aviso);
  }));
  doc.querySelectorAll('a').forEach(a => {
    if (absoluto(a.getAttribute('href'))) {
      a.setAttribute('target', '_blank');
      a.setAttribute('rel', 'noopener noreferrer');
    } else {
      a.replaceWith(...Array.from(a.childNodes));
    }
  });
  return doc.body.innerHTML;
}

/** {@code print%20tela.png} ou {@code /attachments/download/5/print.png} → {@code print tela.png}. */
function nomeDoAnexo(src: string): string {
  const nome = src.split(/[?#]/)[0].split('/').pop() ?? '';
  try {
    return decodeURIComponent(nome);
  } catch {
    return nome;
  }
}
