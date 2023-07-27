program SprintPlanning;

uses
  Vcl.Forms,
  Vcl.Controls,
  System.SysUtils,
  fInicio in 'src\view\fInicio.pas' {frmInicio},
  uConexaoBanco in 'src\Conexao\ConexaoBanco\uConexaoBanco.pas',
  uConsultaSQL in 'src\Conexao\ConsultaSQL\uConsultaSQL.pas',
  uDMConexao in 'src\Conexao\DataModulo\uDMConexao.pas' {dmConexao: TDataModule},
  fPlanning in 'src\view\fPlanning.pas' {frmPlanning},
  uInicio in 'src\Inicio\uInicio.pas',
  dtmPlanning in 'src\Planning\dtmPlanning.pas' {dmPlanning: TDataModule},
  uManipuladorPlanningDAO in 'src\Planning\uManipuladorPlanningDAO.pas',
  uUsuario in 'src\Usuario\uUsuario.pas',
  uModerador in 'src\Usuario\uModerador.pas',
  uJogador in 'src\Usuario\uJogador.pas',
  uObservador in 'src\Usuario\uObservador.pas';

{$R *.res}

begin
  Application.MainFormOnTaskbar := True;
  Application.CreateForm(TdmConexao, dmConexao);
  Application.CreateForm(TfrmInicio, frmInicio);
  Application.CreateForm(TdmPlanning, dmPlanning);
  frmInicio.ShowModal;
  if frmInicio.ModalResult = mrOk then
  begin
    var planning := frmInicio.cbSprint.Text;
    var tpUsuario := frmInicio.rgTpUsuario.ItemIndex;
    var nmUsuario := frmInicio.edtUsuario.Text;
    FreeAndNil(frmInicio);
    Application.Initialize;
    Application.CreateForm(TfrmPlanning, frmPlanning);
    frmPlanning.Sprint := planning;
    frmPlanning.TipoUsuario := tpUsuario;
    frmPlanning.NomeUsuario := nmUsuario;
    Application.Run;
  end;
end.
