// textile-js não publica tipos; só o que textile-redmine.ts usa
declare module 'textile-js' {
  const textile: { convert(texto: string): string };
  export default textile;
}
