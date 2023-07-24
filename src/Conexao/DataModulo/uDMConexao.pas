unit uDMConexao;

interface

uses
  System.SysUtils, System.Classes, FireDAC.Stan.Intf, FireDAC.Stan.Option,
  FireDAC.Stan.Error, FireDAC.UI.Intf, FireDAC.Phys.Intf, FireDAC.Stan.Def,
  FireDAC.Stan.Pool, FireDAC.Stan.Async, FireDAC.Phys, FireDAC.VCLUI.Wait,
  FireDAC.Phys.PGDef, FireDAC.Phys.PG, Data.DB, FireDAC.Comp.Client, IniFiles;

type
  TdmConexao = class(TDataModule)
    conexao: TFDConnection;
    driver: TFDPhysPgDriverLink;
    procedure DataModuleCreate(Sender: TObject);
    procedure DataModuleDestroy(Sender: TObject);
  private
    procedure ConectarBanco;
    { Private declarations }
  public
    function GetConexao: TFDConnection;
  end;

var
  dmConexao: TdmConexao;

implementation

{%CLASSGROUP 'Vcl.Controls.TControl'}

{$R *.dfm}

procedure TdmConexao.DataModuleCreate(Sender: TObject);
begin
  ConectarBanco;
end;

procedure TdmConexao.DataModuleDestroy(Sender: TObject);
begin
  conexao.Free;
  driver.Free;
end;

function TdmConexao.GetConexao: TFDConnection;
begin
  Result := conexao;
end;

procedure TdmConexao.ConectarBanco;
var
  conexaoIni: TIniFile;
begin
  conexaoIni := TIniFile.Create('.\conexao.ini');
  try
    try
      conexao.Params.Values['Server'] := conexaoIni.ReadString('configuracoes', 'servidor', conexao.Params.Values['Server']);
      conexao.Params.Database := conexaoIni.ReadString('configuracoes', 'banco', conexao.Params.Database);
      conexao.Params.UserName := conexaoIni.ReadString('configuracoes', 'usuario', conexao.Params.UserName);
      conexao.Params.Password := conexaoIni.ReadString('configuracoes', 'senha', conexao.Params.Password);
      conexao.Params.Values['Port'] := conexaoIni.ReadString('configuracoes', 'porta', conexao.Params.Values['Port']);
      driver.VendorLib := '.\lib\libpq.dll';
      conexao.TxOptions.AutoCommit := False;
      conexao.Connected := True;
    except
      on e: Exception do
      begin
        var msg := 'Erro ao conectar no banco de dados ' + conexao.Params.Database + ''#13'' + ' Verifique o arquivo de conexao.' + ''#13'';
        raise Exception.Create(msg + e.Message);
      end;
    end;
  finally
    conexaoIni.Free;
  end;
end;

end.
