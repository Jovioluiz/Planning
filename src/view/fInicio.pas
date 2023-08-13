unit fInicio;

interface

uses
  Winapi.Windows, Winapi.Messages, System.SysUtils, System.Variants, System.Classes, Vcl.Graphics,
  Vcl.Controls, Vcl.Forms, Vcl.Dialogs, Vcl.StdCtrls, Vcl.ExtCtrls, Vcl.Buttons;

type
  TfrmInicio = class(TForm)
    pnlFundo: TPanel;
    cbSprint: TComboBox;
    Label1: TLabel;
    Label2: TLabel;
    rgTpUsuario: TRadioGroup;
    edtUsuario: TEdit;
    btnEntrar: TButton;
    procedure FormCreate(Sender: TObject);
    procedure FormKeyPress(Sender: TObject; var Key: Char);
    procedure btnEntrarClick(Sender: TObject);
  private
    procedure Entrar;
    procedure PreencheSprints;
  public
  end;

var
  frmInicio: TfrmInicio;

implementation

uses
  uInicio, System.Generics.Collections;

{$R *.dfm}

procedure TfrmInicio.btnEntrarClick(Sender: TObject);
begin
  Entrar;
end;

procedure TfrmInicio.Entrar;
begin
  if cbSprint.ItemIndex < 0 then
    raise Exception.Create('Informe a sprint');
  if edtUsuario.Text = '' then
    raise Exception.Create('Informe um usuário.');
  ModalResult := mrOk;
end;

procedure TfrmInicio.FormCreate(Sender: TObject);
begin
  PreencheSprints;
end;

procedure TfrmInicio.FormKeyPress(Sender: TObject; var Key: Char);
begin
  if Key = #13 then
  begin
    Key := #0;
    Perform(WM_NEXTDLGCTL,0,0)
  end;
end;

procedure TfrmInicio.PreencheSprints;
var
  inicio: TInicio;
  sprint: TList<string>;
begin
  inicio := TInicio.Create;
  sprint := nil;
  try
    sprint := inicio.GetSprints;

    for var s in sprint do
      cbSprint.Items.Add(s);
  finally
    inicio.Free;
    sprint.Free;
  end;
end;

end.
