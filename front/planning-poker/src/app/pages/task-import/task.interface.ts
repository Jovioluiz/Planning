interface ITask {
  id: number;
  numero: number;
  titulo: string;
  descricao: string;
  prioridade: string | null;
  status: string | null;
  estimada: boolean;
  liberada: boolean;
}