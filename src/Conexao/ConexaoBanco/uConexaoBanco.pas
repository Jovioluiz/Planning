unit uConexaoBanco;

interface

uses
  FireDAC.Comp.Client, uDMConexao;

type
  TConexaoBanco = class
    private
      class var FInstancia: TConexaoBanco;
      strict private FConexao: TFDConnection;
      strict private FDataModule: TdmConexao;
      strict private FNomeBanco: string;
      class function GetInstancia: TConexaoBanco; static;
      constructor Create;
    public
      destructor Destroy; override;
      function Conectar: Boolean;
      property Conexao: TFDConnection read FConexao;
      property NomeBanco: string read FNomeBanco;
      class property Instancia: TConexaoBanco read GetInstancia;
  end;

implementation


{ TConexaoBanco }

function TConexaoBanco.Conectar: Boolean;
begin
  Result := Assigned(FConexao);
end;

constructor TConexaoBanco.Create;
begin
  FDataModule := TdmConexao.Create(nil);
  FConexao := FDataModule.GetConexao;
  FNomeBanco := FConexao.Params.Database;
end;

destructor TConexaoBanco.Destroy;
begin
  if Assigned(FConexao) then
  begin
    if FConexao.Connected then
      FConexao.Close;
    FConexao.Free;
  end;
  if Assigned(FDataModule) then
    FDataModule.Free;
  inherited;
end;

class function TConexaoBanco.GetInstancia: TConexaoBanco;
begin
  if not Assigned(FInstancia) then
    FInstancia := TConexaoBanco.Create;
  Result := FInstancia;
end;


initialization

finalization
  if Assigned(TConexaoBanco.FInstancia) then
    TConexaoBanco.FInstancia.Free;

end.
