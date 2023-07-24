unit uInicio;

interface

uses
  System.Generics.Collections;

type
  TInicio = class

  public
    function GetSprints: TList<string>;
  end;

implementation

uses
  uConsultaSQL;

{ TInicio }

function TInicio.GetSprints: TList<string>;
const
  SQL = 'SELECT nr_sprint FROM sprints';
var
  consulta: TConsultaSQL;
begin
  consulta := TConsultaSQL.GetConsultaSQL();
  Result := TList<string>.Create;
  try
    consulta.Open(SQL);

    consulta.First;
    while not consulta.Eof do
    begin
      if not Result.Contains(consulta.FieldByName('nr_sprint').AsString) then
        Result.Add(consulta.FieldByName('nr_sprint').AsString);
      consulta.Next;
    end;
  finally
    consulta.Free;
  end;
end;

end.
