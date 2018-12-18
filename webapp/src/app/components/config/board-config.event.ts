export class BoardConfigEvent {
  constructor(
    public type: BoardConfigType,
    public templateId: number,
    public boardId: number,
    public payload: any) {
  }
}

export enum BoardConfigType {
  DELETE,
  SAVE,
  NEW
}
