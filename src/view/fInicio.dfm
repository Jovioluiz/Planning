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
    object cbSprint: TComboBox
      Left = 80
      Top = 31
      Width = 177
      Height = 22
      Style = csOwnerDrawFixed
      TabOrder = 0
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
    object edtUsuario: TEdit
      Left = 80
      Top = 71
      Width = 177
      Height = 21
      CharCase = ecUpperCase
      TabOrder = 1
    end
    object btnEntrar: TButton
      Left = 120
      Top = 98
      Width = 89
      Height = 31
      Caption = 'Entrar'
      TabOrder = 3
      OnClick = btnEntrarClick
    end
  end
end
