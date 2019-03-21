import {Action, createSelector} from '@ngrx/store';
import {List} from 'immutable';
import {AffectsVersionState, AffectsVersionUtil, initialAffectsVersionState} from './affects-version.model';
import {AppState} from '../../../../app-store';


const DESERIALIZE_ALL_AFFECTS_VERSIONS = 'DESERIALIZE_ALL_AFFECTS_VERSIONS';
const ADD_AFFECTS_VERSIONS = 'ADD_AFFECTS_VERSIONS';

class DeserializeAffectsVersionsAction implements Action {
  readonly type = DESERIALIZE_ALL_AFFECTS_VERSIONS;

  constructor(readonly payload: List<string>) {
  }
}

class AddAffectsVersionsAction implements Action {
  readonly type = ADD_AFFECTS_VERSIONS;

  constructor(readonly payload: List<string>) {
  }
}

export class AffectsVersionActions {
  static createDeserializeAffectsVersions(input: any): Action {
    const inputArray: string[] = input ? input : [];
    return new DeserializeAffectsVersionsAction(List<string>(inputArray));
  }

  static createAddAffectsVersions(input: any): Action {
    const inputArray: string[] = input ? input : [];
    return new AddAffectsVersionsAction(List<string>(inputArray));
  }
}

// 'meta-reducer here means it is not called directly by the store, rather from the boardReducer
export function affectsVersionMetaReducer(state: AffectsVersionState = initialAffectsVersionState, action: Action): AffectsVersionState {

  switch (action.type) {
    case DESERIALIZE_ALL_AFFECTS_VERSIONS: {
      const payload: List<string> = (<DeserializeAffectsVersionsAction>action).payload;
      return AffectsVersionUtil.withMutations(state, mutable => {
        if (!mutable.versions.equals(payload)) {
          mutable.versions = payload;
        }
      });
    }
    case ADD_AFFECTS_VERSIONS: {
      const payload: List<string> = (<AddAffectsVersionsAction>action).payload;
      if (payload.size > 0) {

        const newAffectsVersions: List<string> = state.versions.concat(payload)
          .sort((a, b) => a.toLocaleLowerCase().localeCompare(b.toLocaleLowerCase())).toList();
        return AffectsVersionUtil.withMutations(state, mutable => {
          mutable.versions = newAffectsVersions;
        });
      }
      return state;

    }
    default:
      return state;
  }
}

const getAffectsVersionsState = (state: AppState) => state.board.affectsVersions;
const getAffectsVersions = (state: AffectsVersionState) => state.versions;
export const affectsVersionsSelector = createSelector(getAffectsVersionsState, getAffectsVersions);
