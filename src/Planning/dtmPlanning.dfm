object dmPlanning: TdmPlanning
  OldCreateOrder = False
  Height = 150
  Width = 215
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
    Left = 64
    Top = 8
  end
end
