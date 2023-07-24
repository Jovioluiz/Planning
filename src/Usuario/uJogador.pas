unit uJogador;

interface

uses
  uUsuario;

type
  TJogador = class(TInterfacedObject, IUsuario)
  private
    FCodigo: Integer;
    FNome: string;
    FModerador,
    FObservador,
    FJogador: Boolean;
  public
    function GetUsuario: IUsuario;

    procedure SetModerador(const Moder: Boolean);
    procedure SetObservador(const Observer: Boolean);
    procedure SetJogador(const Player: Boolean);
    procedure SetCodigo(const Cod: Integer);
    procedure SetNome(const NomeJogador: string);

    function GetModerador: Boolean;
    function GetObservador: Boolean;
    function GetJogador: Boolean;
    function GetCodigo: Integer;
    function GetNome: string;

    property Codigo: Integer read GetCodigo write SetCodigo;
    property Nome: string read GetNome write SetNome;
    property Moderador: Boolean read GetModerador write SetModerador;
    property Observador: Boolean read GetObservador write SetObservador;
    property Jogador: Boolean read GetJogador write SetJogador;
  end;

implementation

{ TJogador }

function TJogador.GetCodigo: Integer;
begin
  Result := FCodigo;
end;

function TJogador.GetJogador: Boolean;
begin
  Result := FJogador;
end;

function TJogador.GetModerador: Boolean;
begin
  Result := FModerador;
end;

function TJogador.GetNome: string;
begin
  Result := FNome;
end;

function TJogador.GetObservador: Boolean;
begin
  Result := FObservador;
end;

function TJogador.GetUsuario: IUsuario;
begin
  Result := Self;
end;

procedure TJogador.SetCodigo(const Cod: Integer);
begin
  FCodigo := Cod;
end;

procedure TJogador.SetJogador(const Player: Boolean);
begin
  FJogador := Player;
end;

procedure TJogador.SetModerador(const Moder: Boolean);
begin
  FModerador := Moder;
end;

procedure TJogador.SetNome(const NomeJogador: string);
begin
  FNome := NomeJogador;
end;

procedure TJogador.SetObservador(const Observer: Boolean);
begin
  FObservador := Observer;
end;

end.
