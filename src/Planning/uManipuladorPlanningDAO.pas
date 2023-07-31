unit uManipuladorPlanningDAO;

interface

uses
  dtmPlanning, Data.DB, Firedac.Stan.Param;

type
  TTipoUsuario = (tuModerador, tuJogador, tuObservador);
  TManipuladorPlanning = class

  private
    FDados: TdmPlanning;
    function GetCodCadastraUsuario(const NomeUsuario: string; const TipoJogador: TTipoUsuario): Integer;
    function GetListaChamadosSprint(const Sprint: string): TArray<Integer>;
  public
    constructor Create;
    destructor Destroy; override;
    procedure CarregaUsuariosPlanning(const Sprint: string);
    procedure CarregaChamadosAtivos(const Sprint: string);
    procedure VerificaUsuarioCadastradoSprint(const CodUsuario: Integer; const Sprint: string);
    procedure AtualizaEstimativaChamado(const CodUsuario, NrChamado: Integer; const Planning: Double);
    procedure IniciaEstimativaChamado(const Sprint: string; const NrChamado: Integer);
    function CadastraOuGetCodUsuario(const NomeUsuario: string; const TipoJogador: TTipoUsuario): Integer;
    function GetChamadoAtivo(const Sprint: string): Integer;
    procedure PreencheDataSetVotacao(const Sprint: string; const NrChamado: Integer);
    procedure FinalizaVotacaoChamado(const Sprint: string; const NrChamado: Integer);
    property Dados: TdmPlanning read FDados write FDados;
  end;

implementation

uses
  uConsultaSQL;

{ TManipuladorPlanning }

procedure TManipuladorPlanning.AtualizaEstimativaChamado(const CodUsuario, NrChamado: Integer; const Planning: Double);
const
  UPDATE = ' UPDATE ' +
           ' 	estimativa_chamado_usuarios ' +
           ' SET ' +
           ' 	planning = :planning ' +
           ' WHERE ' +
           ' 	cd_usuario = :cd_usuario ' +
           ' 	AND nr_chamado = :nr_chamado ';
var
  consulta: TConsultaSQL;
begin
  consulta := TConsultaSQL.GetConsultaSQL(UPDATE);

  try
    consulta.ExecSQL(UPDATE, [Planning, CodUsuario, NrChamado]);
  finally
    consulta.Free;
  end;
end;

function TManipuladorPlanning.CadastraOuGetCodUsuario(const NomeUsuario: string; const TipoJogador: TTipoUsuario): Integer;
const
  SQL = 'SELECT cd_usuario FROM usuario WHERE upper(nm_usuario) = upper(:nm_usuario)';
  SQL_ATUALIZA = ' UPDATE usuario ' +
                 ' SET moderador = :moderador, observador = :observador, jogador = :jogador ' +
                 ' WHERE cd_usuario = :cd_usuario ';
var
  consulta,
  update: TConsultaSQL;
begin
  consulta := TConsultaSQL.GetConsultaSQL();
  update := TConsultaSQL.GetConsultaSQL(SQL_ATUALIZA);
  try
    consulta.Open(SQL, [NomeUsuario]);
    if not consulta.IsEmpty then
    begin
      update.ParamByName('moderador').AsBoolean := False;
      update.ParamByName('observador').AsBoolean := False;
      update.ParamByName('jogador').AsBoolean := False;
      case TipoJogador of
        tuModerador: update.ParamByName('moderador').AsBoolean := True;
        tuObservador: update.ParamByName('observador').AsBoolean := True;
        tuJogador: update.ParamByName('jogador').AsBoolean := True;
      end;
      update.ParamByName('cd_usuario').AsInteger := consulta.FieldByName('cd_usuario').AsInteger;
      update.ExecSQL;
      Exit(consulta.FieldByName('cd_usuario').AsInteger);
    end;

    Result := GetCodCadastraUsuario(NomeUsuario, TipoJogador);
  finally
    consulta.Free;
    update.Free;
  end;
end;

function TManipuladorPlanning.GetChamadoAtivo(const Sprint: string): Integer;
const
  SQL = ' SELECT ' +
        ' 	nr_chamado ' +
        ' FROM  ' +
        ' 	lista_chamados ' +
        ' WHERE ' +
        ' 	nr_sprint = :nr_sprint ' +
        ' 	AND chamado_votacao ';
var
  consulta: TConsultaSQL;
begin
  consulta := TConsultaSQL.GetConsultaSQL(SQL);

  try
    consulta.Open(SQL, [Sprint]);
    Result := consulta.FieldByName('nr_chamado').AsInteger;
  finally
    consulta.Free;
  end;
end;

function TManipuladorPlanning.GetCodCadastraUsuario(const NomeUsuario: string; const TipoJogador: TTipoUsuario): Integer;
const
  SQL = ' INSERT ' +
        ' 	INTO ' +
        ' 	usuario ( ' +
        ' 		cd_usuario, ' +
        ' 		nm_usuario, ' +
        ' 		moderador, ' +
        ' 		observador, ' +
        ' 		jogador ' +
        ' 	) ' +
        ' VALUES ( ' +
        ' 	( ' +
        ' 		SELECT ' +
        ' 			max(cd_usuario)+ 1 ' +
        ' 		FROM ' +
        ' 			usuario ' +
        ' 	), ' +
        ' 	:nm_usuario, ' +
        ' 	:moderador, ' +
        ' 	:observador, ' +
        ' 	:jogador ' +
        ' ) RETURNING cd_usuario ';
var
  consulta: TConsultaSQL;
begin
  consulta := TConsultaSQL.GetConsultaSQL(SQL);

  try
    consulta.ParamByName('nm_usuario').AsString := NomeUsuario;
    consulta.ParamByName('moderador').AsBoolean := False;
    consulta.ParamByName('observador').AsBoolean := False;
    consulta.ParamByName('jogador').AsBoolean := False;
    case TipoJogador of
      tuModerador: consulta.ParamByName('moderador').AsBoolean := True;
      tuObservador: consulta.ParamByName('observador').AsBoolean := True;
      tuJogador: consulta.ParamByName('jogador').AsBoolean := True;
    end;
    consulta.Open;
    Result := consulta.FieldByName('cd_usuario').AsInteger;
  finally
    consulta.Free;
  end;
end;

function TManipuladorPlanning.GetListaChamadosSprint(const Sprint: string): TArray<Integer>;
const
  SQL = ' SELECT ' +
        ' 	DISTINCT nr_chamado ' +
        ' FROM ' +
        ' 	lista_chamados ' +
        ' WHERE ' +
        ' 	nr_sprint = :nr_sprint ';
var
  consulta: TConsultaSQL;
begin
  consulta := TConsultaSQL.GetConsultaSQL(SQL);
  Setlength(Result, 0);
  try
    consulta.Open(SQL, [Sprint]);
    if consulta.IsEmpty then
      Exit;

    Setlength(Result, consulta.RecordCount);

    consulta.First;
    while not consulta.Eof do
    begin
      Result[consulta.RecNo-1] := consulta.FieldByName('nr_chamado').AsInteger;
      consulta.Next;
    end;
  finally
    consulta.Free;
  end;
end;

procedure TManipuladorPlanning.IniciaEstimativaChamado(const Sprint: string; const NrChamado: Integer);
const
  SQL = ' UPDATE ' +
        ' 	lista_chamados ' +
        ' SET ' +
        ' 	chamado_votacao = TRUE  ' +
        ' WHERE ' +
        ' 	nr_sprint = :nr_sprint ' +
        ' 	AND nr_chamado = :nr_chamado ';
var
  consulta: TConsultaSQL;
begin
  consulta := TConsultaSQL.GetConsultaSQL();

  try
    consulta.ExecSQL(SQL, [Sprint, NrChamado]);
  finally
    consulta.Free;
  end;
end;

procedure TManipuladorPlanning.PreencheDataSetVotacao(const Sprint: string; const NrChamado: Integer);
const
  SQL = ' SELECT ' +
        ' 	ecu.cd_usuario, ' +
        ' 	ecu.planning  ' +
        ' 	FROM lista_chamados lc ' +
        ' JOIN estimativa_chamado_usuarios ecu ON	lc.nr_chamado = ecu.nr_chamado ' +
        ' WHERE ' +
        ' 	lc.nr_sprint = :nr_sprint ' +
        ' 	AND lc.nr_chamado = :nr_chamado ';
var
  consulta: TConsultaSQL;
  book: TBookmark;
begin
  consulta := TConsultaSQL.GetConsultaSQL();
  FDados.cdsUsuariosPlanning.DisableControls;

  try
    consulta.Open(SQL, [Sprint, NrChamado]);
    consulta.First;
    book := FDados.cdsUsuariosPlanning.GetBookmark;
    while not consulta.Eof do
    begin
      if FDados.cdsUsuariosPlanning.Locate('cd_usuario', consulta.FieldByName('cd_usuario').AsInteger, []) then
        FDados.cdsUsuariosPlanning.Edit;
      FDados.cdsUsuariosPlanning.FieldByName('planning').AsFloat := consulta.FieldByName('planning').AsFloat;
      FDados.cdsUsuariosPlanning.Post;
      consulta.Next;
    end;
    if FDados.cdsUsuariosPlanning.BookmarkValid(book) then
      FDados.cdsUsuariosPlanning.GotoBookmark(book);
  finally
    FDados.cdsUsuariosPlanning.EnableControls;
    consulta.Free;
    FDados.cdsUsuariosPlanning.FreeBookmark(book);
  end;
end;

procedure TManipuladorPlanning.VerificaUsuarioCadastradoSprint(const CodUsuario: Integer; const Sprint: string);
const
  SQL = ' SELECT ' +
        ' 	1 ' +
        ' FROM ' +
        ' 	estimativa_chamado_usuarios ecu ' +
        ' JOIN lista_chamados lc ON ' +
        ' 	ecu.nr_chamado = lc.nr_chamado ' +
        ' WHERE ' +
        ' 	ecu.cd_usuario = :cd_usuario  ' +
        ' 	AND lc.nr_sprint = :nr_sprint ';

  INSERT = ' INSERT ' +
           ' 	INTO ' +
           ' 	estimativa_chamado_usuarios(cd_usuario,	nr_chamado) ' +
           ' VALUES(:cd_usuario, :nr_chamado) ';
var
  consulta: TConsultaSQL;
  chamados: TArray<Integer>;
  j: Integer;
begin
  consulta := TConsultaSQL.GetConsultaSQL();

  try
    consulta.Open(SQL, [CodUsuario, Sprint]);
    if not consulta.IsEmpty then
      Exit;

    consulta.Close;
    consulta.SQL.Text := INSERT;
    chamados := GetListaChamadosSprint(Sprint);

    consulta.Params.ArraySize := Length(chamados);
    for j := 0 to High(chamados) do
    begin
      consulta.ParamByName('cd_usuario').AsIntegers[j] := CodUsuario;
      consulta.ParamByName('nr_chamado').AsIntegers[j] := chamados[j];
    end;

    consulta.Execute(Length(chamados), 0);
  finally
    consulta.Free;
  end;
end;

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
        '   DISTINCT ' +
        ' 	ecu.cd_usuario, ' +
        ' 	u.nm_usuario, ' +
        '   u.moderador ' +
        ' FROM ' +
        ' 	estimativa_chamado_usuarios ecu ' +
        ' JOIN lista_chamados lc ON ecu.nr_chamado = lc.nr_chamado ' +
        ' JOIN usuario u ON ecu.cd_usuario = u.cd_usuario ' +
        ' WHERE ' +
        ' 	lc.nr_sprint = :nr_sprint ' +
        ' ORDER BY u.moderador DESC' ;
var
  consulta: TConsultaSQL;
begin
  consulta := TConsultaSQL.GetConsultaSQL();
  try
    consulta.Open(SQL, [Sprint]);

    consulta.First;
    while not consulta.Eof do
    begin
      FDados.cdsUsuariosPlanning.Append;
      FDados.cdsUsuariosPlanning.FieldByName('cd_usuario').AsInteger := consulta.FieldByName('cd_usuario').AsInteger;
      FDados.cdsUsuariosPlanning.FieldByName('nm_usuario').AsString := consulta.FieldByName('nm_usuario').AsString;
      FDados.cdsUsuariosPlanning.FieldByName('moderador').AsBoolean := consulta.FieldByName('moderador').AsBoolean;
      FDados.cdsUsuariosPlanning.Post;
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

procedure TManipuladorPlanning.FinalizaVotacaoChamado(const Sprint: string; const NrChamado: Integer);
const
  UPDATE = ' UPDATE ' +
           ' 	lista_chamados ' +
           ' SET ' +
           ' 	ativo = FALSE, ' +
           ' 	finalizado = TRUE, ' +
           ' 	chamado_votacao = FALSE  ' +
           ' WHERE ' +
           ' 	nr_chamado = :nr_chamado ' +
           ' 	AND nr_sprint = :nr_sprint ';
var
  consulta: TConsultaSQL;
begin
  consulta := TConsultaSQL.GetConsultaSQL();

  try
    if FDados.cdsChamadosAtivos.Locate('nr_chamado', NrChamado, []) then
    begin
      FDados.cdsChamadosAtivos.Edit;
      FDados.cdsChamadosAtivos.FieldByName('ativo').AsBoolean := False;
      FDados.cdsChamadosAtivos.FieldByName('finalizado').AsBoolean := True;
      FDados.cdsChamadosAtivos.Post;
      consulta.ExecSQL(UPDATE, [NrChamado, Sprint]);
    end;
  finally
    consulta.Free;
  end;
end;

end.
