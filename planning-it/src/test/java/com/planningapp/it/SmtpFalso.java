package com.planningapp.it;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Servidor SMTP mínimo (sem STARTTLS nem autenticação) que guarda as mensagens recebidas, para testar o e-mail do
 * "Esqueci minha senha" sem um servidor de e-mail de verdade. O WildFly dos testes aponta para ele (SMTP_HOST).
 */
final class SmtpFalso implements AutoCloseable {

    record Email(String para, String conteudo) {
    }

    private final ServerSocket servidor;
    private final BlockingQueue<Email> recebidos = new LinkedBlockingQueue<>();

    SmtpFalso(int porta) throws IOException {
        servidor = new ServerSocket(porta);
        Thread thread = new Thread(this::aceitar, "smtp-falso");
        thread.setDaemon(true);
        thread.start();
    }

    /** Próximo e-mail recebido, ou null se nenhum chegar no prazo. */
    Email proximo(long segundos) throws InterruptedException {
        return recebidos.poll(segundos, TimeUnit.SECONDS);
    }

    void limpar() {
        recebidos.clear();
    }

    private void aceitar() {
        while (!servidor.isClosed()) {
            try (Socket conexao = servidor.accept()) {
                atender(conexao);
            } catch (IOException e) {
                // servidor fechado ou cliente caiu: segue para a próxima
            }
        }
    }

    private void atender(Socket conexao) throws IOException {
        BufferedReader entrada = new BufferedReader(new InputStreamReader(conexao.getInputStream(), StandardCharsets.UTF_8));
        OutputStream saida = conexao.getOutputStream();
        responder(saida, "220 smtp-falso");
        String para = null;
        String linha;
        while ((linha = entrada.readLine()) != null) {
            String comando = linha.toUpperCase();
            if (comando.startsWith("EHLO") || comando.startsWith("HELO")) {
                responder(saida, "250 smtp-falso");
            } else if (comando.startsWith("RCPT TO:")) {
                para = linha.substring(8).replaceAll("[<>\\s]", "");
                responder(saida, "250 OK");
            } else if (comando.equals("DATA")) {
                responder(saida, "354 fim com <CRLF>.<CRLF>");
                StringBuilder conteudo = new StringBuilder();
                while ((linha = entrada.readLine()) != null && !linha.equals(".")) {
                    conteudo.append(linha).append("\r\n");
                }
                recebidos.add(new Email(para, conteudo.toString()));
                responder(saida, "250 OK");
            } else if (comando.equals("QUIT")) {
                responder(saida, "221 tchau");
                return;
            } else {
                responder(saida, "250 OK");
            }
        }
    }

    private static void responder(OutputStream saida, String texto) throws IOException {
        saida.write((texto + "\r\n").getBytes(StandardCharsets.UTF_8));
        saida.flush();
    }

    @Override
    public void close() throws IOException {
        servidor.close();
    }
}
