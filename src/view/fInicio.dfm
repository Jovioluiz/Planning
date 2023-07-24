object frmInicio: TfrmInicio
  Left = 0
  Top = 0
  BorderIcons = [biSystemMenu, biMinimize]
  Caption = 'Inicio'
  ClientHeight = 152
  ClientWidth = 460
  Color = clBtnFace
  Font.Charset = DEFAULT_CHARSET
  Font.Color = clWindowText
  Font.Height = -11
  Font.Name = 'Tahoma'
  Font.Style = []
  KeyPreview = True
  OldCreateOrder = False
  Position = poScreenCenter
  OnCreate = FormCreate
  OnKeyPress = FormKeyPress
  PixelsPerInch = 96
  TextHeight = 13
  object pnlFundo: TPanel
    Left = 0
    Top = 0
    Width = 460
    Height = 152
    Align = alClient
    TabOrder = 0
    ExplicitWidth = 325
    ExplicitHeight = 145
    object Label1: TLabel
      Left = 32
      Top = 35
      Width = 28
      Height = 13
      Caption = 'Sprint'
    end
    object Label2: TLabel
      Left = 24
      Top = 73
      Width = 36
      Height = 13
      Caption = 'Usuario'
    end
    object btnEntrar: TSpeedButton
      Left = 112
      Top = 98
      Width = 105
      Height = 31
      Caption = 'Entrar'
      OnClick = btnEntrarClick
    end
    object cbSprint: TComboBox
      Left = 80
      Top = 31
      Width = 177
      Height = 22
      Style = csOwnerDrawFixed
      TabOrder = 0
    end
    object cbUsuario: TComboBox
      Left = 80
      Top = 70
      Width = 177
      Height = 22
      Style = csOwnerDrawFixed
      TabOrder = 1
    end
    object rgTpUsuario: TRadioGroup
      Left = 263
      Top = 24
      Width = 185
      Height = 105
      Caption = 'Op'#231#245'es'
      ItemIndex = 2
      Items.Strings = (
        'Moderador'
        'Jogador'
        'Observador')
      TabOrder = 2
    end
  end
end
