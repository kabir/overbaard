export class BoardConfigEvent {
  constructor(
    public type: BoardConfigType,
    public templateId: number,
    public boardId: number,
    public payload: any) {
  }
}

export enum BoardConfigType {
  CLEAR_JSON_ERROR,
  DELETE,
  SAVE,
  NEW
}
