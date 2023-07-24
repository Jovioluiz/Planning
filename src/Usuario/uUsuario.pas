unit uUsuario;

interface

type
  IUsuario = interface
  ['{37C1B823-EC0C-419F-8D1F-822E202AB1FF}']
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

    function GetUsuario: IUsuario;
    property Codigo: Integer read GetCodigo write SetCodigo;
    property Nome: string read GetNome write SetNome;
    property Moderador: Boolean read GetModerador write SetModerador;
    property Observador: Boolean read GetObservador write SetObservador;
    property Jogador: Boolean read GetJogador write SetJogador;
  end;

implementation

end.
