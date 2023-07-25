unit fPlanning;

interface

uses
  Winapi.Windows, Winapi.Messages, System.SysUtils, System.Variants, System.Classes, Vcl.Graphics,
  Vcl.Controls, Vcl.Forms, Vcl.Dialogs, Vcl.ExtCtrls, Vcl.ComCtrls, Data.DB,
  Vcl.Grids, Vcl.DBGrids, uManipuladorPlanning, uUsuario, uModerador,
  Vcl.DBCtrls, Vcl.Buttons;

type
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
  private
    FManipuladorPlanning: TManipuladorPlanning;
    FSprint,
    FNomeUsuario: string;
    FTipoUsuario: Integer;
    FUsuario: IUsuario;
    procedure AlteraCorCarta(Sender: TObject);
    procedure PreencheUsuario;
    procedure ConfiguraPlanning;
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

procedure TfrmPlanning.AlteraCorCarta(Sender: TObject);
var
  i: Integer;
  nomePanel: string;
begin
  nomePanel := TPanel(Sender).Name;
  for i := 0 to Pred(pnlCartasPlanning.ControlCount) do
  begin
    if nomePanel <> TPanel(pnlCartasPlanning.Controls[i]).Name then
    begin
      TPanel(pnlCartasPlanning.Controls[i]).Color := clBtnFace;
      TPanel(pnlCartasPlanning.Controls[i]).Repaint;
    end;
  end;
  TPanel(Sender).Color := clHighlight;
  TPanel(Sender).Repaint;
end;

procedure TfrmPlanning.ConfiguraPlanning;
begin
  pnlSuperior.Visible := FUsuario.Moderador;
  pnlSprint.Caption := FSprint + ' Planning';
end;

procedure TfrmPlanning.FormCreate(Sender: TObject);
begin
  FManipuladorPlanning := TManipuladorPlanning.Create;
  grdPlanningUsuarios.DataSource := FManipuladorPlanning.Dados.dsPlanning;
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
  PreencheUsuario;
  FUsuario := FUsuario.GetUsuario;
  ConfiguraPlanning;
  FManipuladorPlanning.CarregaUsuariosPlanning(FSprint);
  FManipuladorPlanning.CarregaChamadosAtivos(FSprint);
end;

procedure TfrmPlanning.pnl0Click(Sender: TObject);
begin
  AlteraCorCarta(Sender);
end;

procedure TfrmPlanning.pnl13Click(Sender: TObject);
begin
  AlteraCorCarta(Sender);
end;

procedure TfrmPlanning.pnl1Click(Sender: TObject);
begin
  AlteraCorCarta(Sender);
end;

procedure TfrmPlanning.pnl20Click(Sender: TObject);
begin
  AlteraCorCarta(Sender);
end;

procedure TfrmPlanning.pnl2Click(Sender: TObject);
begin
  AlteraCorCarta(Sender);
end;

procedure TfrmPlanning.pnl3Click(Sender: TObject);
begin
  AlteraCorCarta(Sender);
end;

procedure TfrmPlanning.pnl40Click(Sender: TObject);
begin
  AlteraCorCarta(Sender);
end;

procedure TfrmPlanning.pnl5Click(Sender: TObject);
begin
  AlteraCorCarta(Sender);
end;

procedure TfrmPlanning.pnl8Click(Sender: TObject);
begin
  AlteraCorCarta(Sender);
end;

procedure TfrmPlanning.pnlMeioClick(Sender: TObject);
begin
  AlteraCorCarta(Sender);
end;

procedure TfrmPlanning.PreencheUsuario;
begin
//  FUsuario.Codigo:= FManipuladorPlanning.GetCodUsuario(FNomeUsuario);
  FUsuario.Nome := FNomeUsuario;
  FUsuario.Moderador := (FUsuario is TModerador);
  FUsuario.Observador := (FUsuario is TObservador);
  FUsuario.Jogador := (FUsuario is TJogador);
end;

end.
