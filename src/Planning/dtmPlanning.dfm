object dmPlanning: TdmPlanning
  OldCreateOrder = False
  Height = 247
  Width = 488
  object dsPlanning: TDataSource
    DataSet = cdsPlanning
    Left = 16
    Top = 8
  end
  object cdsPlanning: TClientDataSet
    PersistDataPacket.Data = {
      C70000009619E0BD010000001800000008000000000003000000C7000A63645F
      7573756172696F04000100000000000A6E6D5F7573756172696F010049000000
      010005574944544802000200140013657374696D61746976615F706C616E6E69
      6E6708000400000000000E74656D706F5F657374696D61646F08000400000000
      000A6E725F6368616D61646F0400010000000000096D6F64657261646F720200
      0300000000000A6F627365727661646F720200030000000000076A6F6761646F
      7202000300000000000000}
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
        Name = 'estimativa_planning'
        DataType = ftFloat
      end
      item
        Name = 'tempo_estimado'
        DataType = ftFloat
      end
      item
        Name = 'nr_chamado'
        DataType = ftInteger
      end
      item
        Name = 'moderador'
        DataType = ftBoolean
      end
      item
        Name = 'observador'
        DataType = ftBoolean
      end
      item
        Name = 'jogador'
        DataType = ftBoolean
      end>
    IndexDefs = <>
    Params = <>
    StoreDefs = True
    Left = 72
    Top = 8
  end
  object dsChamadosAtivos: TDataSource
    DataSet = cdsChamadosAtivos
    Left = 168
    Top = 16
  end
  object cdsChamadosAtivos: TClientDataSet
    PersistDataPacket.Data = {
      740000009619E0BD01000000180000000400000000000300000074000A6E725F
      6368616D61646F04000100000000001164657363726963616F5F6368616D6164
      6F010049000000010005574944544802000200C80005617469766F0200030000
      0000000A66696E616C697A61646F02000300000000000000}
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
      end>
    IndexDefs = <>
    Params = <>
    StoreDefs = True
    Left = 264
    Top = 16
  end
end
