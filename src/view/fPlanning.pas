unit fPlanning;

interface

uses
  Winapi.Windows, Winapi.Messages, System.SysUtils, System.Variants, System.Classes, Vcl.Graphics,
  Vcl.Controls, Vcl.Forms, Vcl.Dialogs, Vcl.ExtCtrls, Vcl.ComCtrls, Data.DB,
  Vcl.Grids, Vcl.DBGrids, uManipuladorPlanningDAO, uUsuario, uModerador,
  Vcl.DBCtrls, Vcl.Buttons, Vcl.StdCtrls;

type
  TCartaPlanning = (cpZero, cpMeio, cpUm, cpDois, cpTres, cpCinco, cpOito, cpTreze, cpVinte, cpQuarenta);
  TfrmPlanning = class(TForm)
    pnlFundo: TPanel;
    pcPlanning: TPageControl;
    tsPlanning: TTabSheet;
    tsHoras: TTabSheet;
    pnlTitulo: TPanel;
    pnl0: TPanel;
    pnlMeio: TPanel;
    pnl1: TPanel;
    pnl2: TPanel;
    pnl3: TPanel;
    pnlCartasPlanning: TPanel;
    pnlDireito: TPanel;
    pnl40: TPanel;
    pnl20: TPanel;
    pnl13: TPanel;
    pnl8: TPanel;
    pnl5: TPanel;
    pnlSuperior: TPanel;
    pnlInferior: TPanel;
    grdPlanningUsuarios: TDBGrid;
    pnlSprint: TPanel;
    PageControl1: TPageControl;
    TabSheet1: TTabSheet;
    grdChamadosAtivos: TDBGrid;
    TabSheet2: TTabSheet;
    grdChamadosFinalizados: TDBGrid;
    btnProximo: TSpeedButton;
    btnAnterior: TSpeedButton;
    btnIniciar: TSpeedButton;
    btnParar: TSpeedButton;
    timerUsuario: TTimer;
    btnImportarChamados: TButton;
    dialog: TFileOpenDialog;
    procedure pnl0Click(Sender: TObject);
    procedure pnlMeioClick(Sender: TObject);
    procedure pnl1Click(Sender: TObject);
    procedure pnl2Click(Sender: TObject);
    procedure pnl3Click(Sender: TObject);
    procedure pnl5Click(Sender: TObject);
    procedure pnl8Click(Sender: TObject);
    procedure pnl13Click(Sender: TObject);
    procedure pnl20Click(Sender: TObject);
    procedure pnl40Click(Sender: TObject);
    procedure FormCreate(Sender: TObject);
    procedure FormDestroy(Sender: TObject);
    procedure FormShow(Sender: TObject);
    procedure btnIniciarClick(Sender: TObject);
    procedure timerUsuarioTimer(Sender: TObject);
    procedure btnPararClick(Sender: TObject);
    procedure btnProximoClick(Sender: TObject);
    procedure btnImportarChamadosClick(Sender: TObject);
  private
    FManipuladorPlanning: TManipuladorPlanning;
    FSprint,
    FNomeUsuario: string;
    FTipoUsuario,
    FNumeroChamadoAtivo: Integer;
    FUsuario: IUsuario;
    procedure AlteraCorCarta(Sender: TObject; Carta: TCartaPlanning);
    procedure PreencheUsuario;
    procedure ConfiguraPlanning;
    procedure IniciarEstimativaChamado;
    procedure AdicionaVotacaoUsuario(const Carta: TCartaPlanning);
    procedure VerificaFinalizouVotacao;
    procedure ProximoChamado;
    procedure BuscaArquivoChamados;
  public
    property Sprint: string read FSprint write FSprint;
    property ManipuladorPlanning: TManipuladorPlanning read FManipuladorPlanning write FManipuladorPlanning;
    property NomeUsuario: string read FNomeUsuario write FNomeUsuario;
    property TipoUsuario: Integer read FTipoUsuario write FTipoUsuario;
  end;

var
  frmPlanning: TfrmPlanning;

implementation

uses
  uJogador, uObservador;

{$R *.dfm}

procedure TfrmPlanning.AdicionaVotacaoUsuario(const Carta: TCartaPlanning);
begin
  FManipuladorPlanning.Dados.cdsUsuariosPlanning.Locate('cd_usuario', FUsuario.Codigo, []);
  FManipuladorPlanning.Dados.cdsUsuariosPlanning.Edit;
  case Carta of
    cpZero: FManipuladorPlanning.Dados.cdsUsuariosPlanning.FieldByName('planning').AsFloat := 0;
    cpMeio: FManipuladorPlanning.Dados.cdsUsuariosPlanning.FieldByName('planning').AsFloat := 1.2;
    cpUm: FManipuladorPlanning.Dados.cdsUsuariosPlanning.FieldByName('planning').AsFloat := 1;
    cpDois: FManipuladorPlanning.Dados.cdsUsuariosPlanning.FieldByName('planning').AsFloat := 2;
    cpTres: FManipuladorPlanning.Dados.cdsUsuariosPlanning.FieldByName('planning').AsFloat := 3;
    cpCinco: FManipuladorPlanning.Dados.cdsUsuariosPlanning.FieldByName('planning').AsFloat := 5;
    cpOito: FManipuladorPlanning.Dados.cdsUsuariosPlanning.FieldByName('planning').AsFloat := 8;
    cpTreze: FManipuladorPlanning.Dados.cdsUsuariosPlanning.FieldByName('planning').AsFloat := 13;
    cpVinte: FManipuladorPlanning.Dados.cdsUsuariosPlanning.FieldByName('planning').AsFloat := 20;
    cpQuarenta: FManipuladorPlanning.Dados.cdsUsuariosPlanning.FieldByName('planning').AsFloat := 40;
  end;
  FManipuladorPlanning.Dados.cdsUsuariosPlanning.Post;
  FManipuladorPlanning.AtualizaEstimativaChamado(FUsuario.Codigo,
                                                 FNumeroChamadoAtivo,
                                                 FManipuladorPlanning.Dados.cdsUsuariosPlanning.FieldByName('planning').AsFloat);
end;

procedure TfrmPlanning.AlteraCorCarta(Sender: TObject; Carta: TCartaPlanning);
var
  i: Integer;
  nomePanel: string;
begin
  IniciarEstimativaChamado;
  nomePanel := TPanel(Sender).Name;
  for i := 0 to Pred(pnlCartasPlanning.ControlCount) do
  begin
    if nomePanel <> TPanel(pnlCartasPlanning.Controls[i]).Name then
    begin
      TPanel(pnlCartasPlanning.Controls[i]).Color := clBtnFace;
      TPanel(pnlCartasPlanning.Controls[i]).Repaint;
    end;
  end;
  if not (Carta in [cpZero]) then
  begin
    TPanel(Sender).Color := clHighlight;
    TPanel(Sender).Repaint;
  end;
  if FNumeroChamadoAtivo = 0 then
  begin
    FNumeroChamadoAtivo := FManipuladorPlanning.GetChamadoAtivo(FSprint);
    timerUsuario.Enabled := True;
  end;
  if FNumeroChamadoAtivo = 0 then
  begin
    FNumeroChamadoAtivo := FManipuladorPlanning.GetChamadoAtivo(FSprint);
    timerUsuario.Enabled := True;
  end;
  AdicionaVotacaoUsuario(Carta);
end;

procedure TfrmPlanning.btnIniciarClick(Sender: TObject);
begin
  IniciarEstimativaChamado;
end;

procedure TfrmPlanning.btnPararClick(Sender: TObject);
begin
  timerUsuario.Enabled := False;
  FNumeroChamadoAtivo := 0;
end;

procedure TfrmPlanning.btnProximoClick(Sender: TObject);
begin
  ProximoChamado;
end;

procedure TfrmPlanning.BuscaArquivoChamados;
begin
  if dialog.Execute and (dialog.FileName <> '') then
    FManipuladorPlanning.ImportaChamadosArquivo(dialog.FileName, FSprint);
end;

procedure TfrmPlanning.btnImportarChamadosClick(Sender: TObject);
begin
  BuscaArquivoChamados;
end;

procedure TfrmPlanning.ConfiguraPlanning;
begin
  pnlSuperior.Visible := FUsuario.Moderador;
  pnlSprint.Caption := FSprint + ' Planning';
end;

procedure TfrmPlanning.FormCreate(Sender: TObject);
begin
  FManipuladorPlanning := TManipuladorPlanning.Create;
  grdPlanningUsuarios.DataSource := FManipuladorPlanning.Dados.dsUsuariosPlanning;
  grdChamadosAtivos.DataSource := FManipuladorPlanning.Dados.dsChamadosAtivos;
end;

procedure TfrmPlanning.FormDestroy(Sender: TObject);
begin
  FManipuladorPlanning.Free;
end;

procedure TfrmPlanning.FormShow(Sender: TObject);
begin
  case TipoUsuario of
    0: FUsuario := TModerador.Create;
    1: FUsuario := TJogador.Create;
    2: FUsuario := TObservador.Create;
  end;
  FNumeroChamadoAtivo := FManipuladorPlanning.GetChamadoAtivo(FSprint);
  PreencheUsuario;
  FUsuario := FUsuario.GetUsuario;
  ConfiguraPlanning;
  FManipuladorPlanning.VerificaUsuarioCadastradoSprint(FUsuario.Codigo, FSprint);
  FManipuladorPlanning.CarregaUsuariosPlanning(FSprint);
  FManipuladorPlanning.CarregaChamadosAtivos(FSprint);
end;

procedure TfrmPlanning.IniciarEstimativaChamado;
begin
  FManipuladorPlanning.Dados.cdsChamadosAtivos.First;
  pnlTitulo.Caption := FManipuladorPlanning.Dados.cdsChamadosAtivos.FieldByName('nr_chamado').AsString
                       + ' - ' + FManipuladorPlanning.Dados.cdsChamadosAtivos.FieldByName('descricao_chamado').AsString;
  FManipuladorPlanning.IniciaEstimativaChamado(FSprint,
                                               FManipuladorPlanning.Dados.cdsChamadosAtivos.FieldByName('nr_chamado').AsInteger);
  FNumeroChamadoAtivo := FManipuladorPlanning.GetChamadoAtivo(FSprint);
  timerUsuario.Enabled := True;
end;

procedure TfrmPlanning.pnl0Click(Sender: TObject);
begin
  AlteraCorCarta(Sender, cpZero);
end;

procedure TfrmPlanning.pnl13Click(Sender: TObject);
begin
  AlteraCorCarta(Sender, cpTreze);
end;

procedure TfrmPlanning.pnl1Click(Sender: TObject);
begin
  AlteraCorCarta(Sender, cpUm);
end;

procedure TfrmPlanning.pnl20Click(Sender: TObject);
begin
  AlteraCorCarta(Sender, cpVinte);
end;

procedure TfrmPlanning.pnl2Click(Sender: TObject);
begin
  AlteraCorCarta(Sender, cpDois);
end;

procedure TfrmPlanning.pnl3Click(Sender: TObject);
begin
  AlteraCorCarta(Sender, cpTres);
end;

procedure TfrmPlanning.pnl40Click(Sender: TObject);
begin
  AlteraCorCarta(Sender, cpQuarenta);
end;

procedure TfrmPlanning.pnl5Click(Sender: TObject);
begin
  AlteraCorCarta(Sender, cpCinco);
end;

procedure TfrmPlanning.pnl8Click(Sender: TObject);
begin
  AlteraCorCarta(Sender, cpOito);
end;

procedure TfrmPlanning.pnlMeioClick(Sender: TObject);
begin
  AlteraCorCarta(Sender, cpMeio);
end;

procedure TfrmPlanning.PreencheUsuario;
var
  usuario: TTipoUsuario;
begin
  FUsuario.Nome := FNomeUsuario;
  FUsuario.Moderador := (FUsuario is TModerador);
  FUsuario.Observador := (FUsuario is TObservador);
  FUsuario.Jogador := (FUsuario is TJogador);
  case TipoUsuario of
    0: usuario := tuModerador;
    1: usuario := tuJogador;
    2: usuario := tuObservador;
    else
      usuario := tuObservador;
  end;
  FUsuario.Codigo := FManipuladorPlanning.CadastraOuGetCodUsuario(FNomeUsuario, usuario);
end;

procedure TfrmPlanning.ProximoChamado;
begin
  try
    FManipuladorPlanning.FinalizaVotacaoChamado(FSprint, FNumeroChamadoAtivo);
    FManipuladorPlanning.Dados.cdsChamadosAtivos.Filter := ' nr_sprint = ' + '''' + FSprint + '''' + ' and not finalizado ';
    FManipuladorPlanning.Dados.cdsChamadosAtivos.Filtered := True;
    FManipuladorPlanning.Dados.cdsChamadosAtivos.First;
    FManipuladorPlanning.IniciaEstimativaChamado(FSprint,
                                                 FManipuladorPlanning.Dados.cdsChamadosAtivos.FieldByName('nr_chamado').AsInteger);
    FNumeroChamadoAtivo := FManipuladorPlanning.GetChamadoAtivo(FSprint);
  except on E: Exception do
    timerUsuario.Enabled := False;
  end;
end;

procedure TfrmPlanning.timerUsuarioTimer(Sender: TObject);
begin
  timerUsuario.Enabled := False;

  try
    VerificaFinalizouVotacao;
  finally
    timerUsuario.Enabled := True;
  end;
end;

procedure TfrmPlanning.VerificaFinalizouVotacao;
var
  jaVotaram: Boolean;
  book: TBookmark;
begin
  if FUsuario.Moderador then
    Exit;
  FManipuladorPlanning.PreencheDataSetVotacao(FSprint, FNumeroChamadoAtivo);
  FManipuladorPlanning.Dados.cdsUsuariosPlanning.DisableControls;
  jaVotaram := True;
  try
    book := FManipuladorPlanning.Dados.cdsUsuariosPlanning.GetBookmark;
    FManipuladorPlanning.Dados.cdsUsuariosPlanning.First;
    while not FManipuladorPlanning.Dados.cdsUsuariosPlanning.Eof do
    begin
      if not FManipuladorPlanning.Dados.cdsUsuariosPlanning.FieldByName('moderador').AsBoolean then
        jaVotaram := jaVotaram and (FManipuladorPlanning.Dados.cdsUsuariosPlanning.FieldByName('planning').AsFloat > 0);
      FManipuladorPlanning.Dados.cdsUsuariosPlanning.Next;
    end;
    if FManipuladorPlanning.Dados.cdsUsuariosPlanning.BookmarkValid(book) then
      FManipuladorPlanning.Dados.cdsUsuariosPlanning.GotoBookmark(book);
    
    grdPlanningUsuarios.Columns.Items[2].Visible := jaVotaram;
    if jaVotaram then
    begin
      timerUsuario.Enabled := False;
      AlteraCorCarta(Self, cpZero);
      ProximoChamado;
    end;

  finally
    FManipuladorPlanning.Dados.cdsUsuariosPlanning.EnableControls;
    FManipuladorPlanning.Dados.cdsUsuariosPlanning.FreeBookmark(book);
  end;
end;

end.
