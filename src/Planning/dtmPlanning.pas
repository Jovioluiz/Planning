unit dtmPlanning;

interface

uses
  System.SysUtils, System.Classes, Data.DB, Datasnap.DBClient;

type
  TdmPlanning = class(TDataModule)
    dsUsuariosPlanning: TDataSource;
    cdsUsuariosPlanning: TClientDataSet;
    dsChamadosAtivos: TDataSource;
    cdsChamadosAtivos: TClientDataSet;
  private
    { Private declarations }
  public
    { Public declarations }
  end;

var
  dmPlanning: TdmPlanning;

implementation

{%CLASSGROUP 'Vcl.Controls.TControl'}

{$R *.dfm}

end.
