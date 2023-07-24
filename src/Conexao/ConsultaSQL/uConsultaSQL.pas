unit uConsultaSQL;

interface

uses
  FireDAC.Comp.Client, FireDAC.DApt, System.Classes;

type
  TConsultaSQL = class(TFDQuery)
    private
      constructor Create(const Conexao: TFDconnection; const SQL: string = ''); reintroduce; overload;
    public
      constructor Create(Owner: TComponent); overload; override;
      class function GetConsultaSQL(const SQL: string = ''): TConsultaSQL;
  end;

implementation

uses
  uConexaoBanco;

{ TConsultaSQL }

constructor TConsultaSQL.Create(Owner: TComponent);
begin
  inherited Create(Owner);
end;

constructor TConsultaSQL.Create(const Conexao: TFDconnection; const SQL: string = '');
begin
  Create(Owner);
  Self.Connection := Conexao;
  Self.SQL.Text := SQL;
end;

class function TConsultaSQL.GetConsultaSQL(const SQL: string = ''): TConsultaSQL;
begin
  Result := TConsultaSQL.Create(TConexaoBanco.Instancia.Conexao, SQL);
end;

end.
