unit uObservador;

interface

uses
  uUsuario;

type
  TObservador = class(TInterfacedObject, IUsuario)
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

{ TObservador }

function TObservador.GetCodigo: Integer;
begin
  Result := FCodigo;
end;

function TObservador.GetJogador: Boolean;
begin
  Result := FJogador;
end;

function TObservador.GetModerador: Boolean;
begin
  Result := FModerador;
end;

function TObservador.GetNome: string;
begin
  Result := FNome;
end;

function TObservador.GetObservador: Boolean;
begin
  Result := FObservador;
end;

function TObservador.GetUsuario: IUsuario;
begin
  Result := Self;
end;

procedure TObservador.SetCodigo(const Cod: Integer);
begin
  FCodigo := Cod;
end;

procedure TObservador.SetJogador(const Player: Boolean);
begin
  FJogador := Player;
end;

procedure TObservador.SetModerador(const Moder: Boolean);
begin
  FModerador := Moder;
end;

procedure TObservador.SetNome(const NomeJogador: string);
begin
  FNome := NomeJogador;
end;

procedure TObservador.SetObservador(const Observer: Boolean);
begin
  FObservador := Observer;
end;

end.
