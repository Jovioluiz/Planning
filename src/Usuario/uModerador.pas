unit uModerador;

interface

uses
  uUsuario;

type
  TModerador = class(TInterfacedObject, IUsuario)
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

{ TModerador }


function TModerador.GetCodigo: Integer;
begin
  Result := FCodigo;
end;

function TModerador.GetJogador: Boolean;
begin
  Result := FJogador;
end;

function TModerador.GetModerador: Boolean;
begin
  Result := FModerador;
end;

function TModerador.GetNome: string;
begin
  Result := FNome;
end;

function TModerador.GetObservador: Boolean;
begin
  Result := FObservador;
end;

function TModerador.GetUsuario: IUsuario;
begin
  Result := Self;
end;

procedure TModerador.SetCodigo(const Cod: Integer);
begin
  FCodigo := Cod;
end;

procedure TModerador.SetJogador(const Player: Boolean);
begin
  FJogador := Player;
end;

procedure TModerador.SetModerador(const Moder: Boolean);
begin
  FModerador := Moder;
end;

procedure TModerador.SetNome(const NomeJogador: string);
begin
  FNome := NomeJogador;
end;

procedure TModerador.SetObservador(const Observer: Boolean);
begin
  FObservador := Observer;
end;

end.
