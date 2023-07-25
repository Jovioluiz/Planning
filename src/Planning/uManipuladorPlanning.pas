unit uManipuladorPlanning;

interface

uses
  dtmPlanning;

type
  TManipuladorPlanning = class

  private
    FDados: TdmPlanning;
  public
    constructor Create;
    destructor Destroy; override;
    procedure CarregaUsuariosPlanning(const Sprint: string);
    procedure CarregaChamadosAtivos(const Sprint: string);
    property Dados: TdmPlanning read FDados write FDados;
  end;

implementation

uses
  uConsultaSQL;

{ TManipuladorPlanning }

procedure TManipuladorPlanning.CarregaChamadosAtivos(const Sprint: string);
const
  SQL = ' SELECT ' +
        ' 	nr_chamado, ' +
        ' 	descricao, ' +
        ' 	ativo, ' +
        ' 	finalizado ' +
        ' FROM ' +
        ' 	lista_chamados ' +
        ' WHERE nr_sprint = :nr_sprint ' +
        ' ORDER BY nr_chamado ';
var
  consulta: TConsultaSQL;
begin
  consulta := TConsultaSQL.GetConsultaSQL();

  try
    consulta.Open(SQL, [Sprint]);

    consulta.First;
    while not consulta.Eof do
    begin
      FDados.cdsChamadosAtivos.Append;
      FDados.cdsChamadosAtivos.FieldByName('nr_chamado').AsString := consulta.FieldByName('nr_chamado').AsString;
      FDados.cdsChamadosAtivos.FieldByName('descricao_chamado').AsString := consulta.FieldByName('descricao').AsString;
      FDados.cdsChamadosAtivos.FieldByName('ativo').AsBoolean := consulta.FieldByName('ativo').AsBoolean;
      FDados.cdsChamadosAtivos.FieldByName('finalizado').AsBoolean := consulta.FieldByName('finalizado').AsBoolean;
      FDados.cdsChamadosAtivos.Post;
      consulta.Next;
    end;
  finally
    consulta.Free;
  end;
end;

procedure TManipuladorPlanning.CarregaUsuariosPlanning(const Sprint: string);
const
  SQL = ' SELECT ' +
        ' 	ecu.cd_usuario, ' +
        ' 	u.nm_usuario, ' +
        ' 	COALESCE(ecu.planning, 0) AS planning, ' +
        ' 	COALESCE(ecu.tempo_estimado, 0) AS tempo_estimado, ' +
        ' 	lc.nr_chamado ' +
        ' FROM ' +
        ' 	estimativa_chamado_usuarios ecu ' +
        ' JOIN lista_chamados lc ON ecu.nr_chamado = lc.nr_chamado ' +
        ' JOIN usuario u ON ecu.cd_usuario = u.cd_usuario ' +
        ' WHERE ' +
        ' 	lc.nr_sprint = :nr_sprint ';
var
  consulta: TConsultaSQL;
begin
  consulta := TConsultaSQL.GetConsultaSQL();

  try
    consulta.Open(SQL, [Sprint]);

    consulta.First;
    while not consulta.Eof do
    begin
      FDados.cdsPlanning.Append;
      FDados.cdsPlanning.FieldByName('cd_usuario').AsInteger := consulta.FieldByName('cd_usuario').AsInteger;
      FDados.cdsPlanning.FieldByName('nm_usuario').AsString := consulta.FieldByName('nm_usuario').AsString;
      FDados.cdsPlanning.FieldByName('estimativa_planning').AsFloat := consulta.FieldByName('planning').AsFloat;
      FDados.cdsPlanning.FieldByName('tempo_estimado').AsFloat := consulta.FieldByName('tempo_estimado').AsFloat;
      FDados.cdsPlanning.FieldByName('nr_chamado').AsString := consulta.FieldByName('nr_chamado').AsString;
      FDados.cdsPlanning.Post;
      consulta.Next;
    end;
  finally
    consulta.Free;
  end;
end;

constructor TManipuladorPlanning.Create;
begin
  FDados := TdmPlanning.Create(nil);
end;

destructor TManipuladorPlanning.Destroy;
begin
  FDados.Free;
  inherited;
end;

end.
