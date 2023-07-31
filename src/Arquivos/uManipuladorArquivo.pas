unit uManipuladorArquivo;

interface

type
  TDadosChamado = record
    NrChamado: Integer;
    DescricaoChamado: string;
  end;
  TManipuladorArquivo = class

    public
      function GetChamadosArquivo(Caminho: string): TArray<TDadosChamado>;
  end;

implementation

uses
  System.Classes, System.SysUtils;

{ TManipuladorArquivo }

function TManipuladorArquivo.GetChamadosArquivo(Caminho: string): TArray<TDadosChamado>;
var
  arquivo, linhas: TStringList;
  temp: TArray<string>;
  i: integer;
  dados: TDadosChamado;
begin
  arquivo := TStringList.Create;
  linhas := TStringList.Create;
  dados := Default(TDadosChamado);
  try
    arquivo.DelimitedText := ';';
    arquivo.LoadFromFile(Caminho);
    SetLength(Result, arquivo.Count);
    for i := 0 to Pred(arquivo.Count) do
    begin
      temp := arquivo.Strings[i].Split([';']);
      dados.NrChamado := StrToInt(temp[0]);
      dados.DescricaoChamado := temp[1];
      Result[i] := dados;
    end;
  finally
    arquivo.Free;
    linhas.Free;
  end;
end;

end.
