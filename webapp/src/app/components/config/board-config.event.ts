import {Subject} from 'rxjs';

export class BoardConfigEvent {

  public readonly eventHandled$: Subject<boolean> = new Subject<boolean>();

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
