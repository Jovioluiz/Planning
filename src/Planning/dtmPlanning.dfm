object dmPlanning: TdmPlanning
  OldCreateOrder = False
  Height = 247
  Width = 488
  object dsUsuariosPlanning: TDataSource
    DataSet = cdsUsuariosPlanning
    Left = 40
    Top = 8
  end
  object cdsUsuariosPlanning: TClientDataSet
    PersistDataPacket.Data = {
      6F0000009619E0BD0100000018000000040000000000030000006F000A63645F
      7573756172696F04000100000000000A6E6D5F7573756172696F010049000000
      010005574944544802000200140008706C616E6E696E67080004000000000009
      6D6F64657261646F7202000300000000000000}
    Active = True
    Aggregates = <>
    FieldDefs = <
      item
        Name = 'cd_usuario'
        DataType = ftInteger
      end
      item
        Name = 'nm_usuario'
        DataType = ftString
        Size = 20
      end
      item
        Name = 'planning'
        DataType = ftFloat
      end
      item
        Name = 'moderador'
        DataType = ftBoolean
      end>
    IndexDefs = <>
    Params = <>
    StoreDefs = True
    Left = 144
    Top = 8
  end
  object dsChamadosAtivos: TDataSource
    DataSet = cdsChamadosAtivos
    Left = 280
    Top = 16
  end
  object cdsChamadosAtivos: TClientDataSet
    PersistDataPacket.Data = {
      920000009619E0BD01000000180000000500000000000300000092000A6E725F
      6368616D61646F04000100000000001164657363726963616F5F6368616D6164
      6F010049000000010005574944544802000200C80005617469766F0200030000
      0000000A66696E616C697A61646F0200030000000000096E725F737072696E74
      01004900000001000557494454480200020014000000}
    Active = True
    Aggregates = <>
    FieldDefs = <
      item
        Name = 'nr_chamado'
        DataType = ftInteger
      end
      item
        Name = 'descricao_chamado'
        DataType = ftString
        Size = 200
      end
      item
        Name = 'ativo'
        DataType = ftBoolean
      end
      item
        Name = 'finalizado'
        DataType = ftBoolean
      end
      item
        Name = 'nr_sprint'
        DataType = ftString
        Size = 20
      end>
    IndexDefs = <>
    Params = <>
    StoreDefs = True
    Left = 384
    Top = 16
  end
end
