import {makeTypedFactory, TypedRecord} from 'typed-immutable-record';
import {List} from 'immutable';


export interface AffectsVersionState {
  versions: List<string>;
}

const DEFAULT_STATE: AffectsVersionState = {
  versions: List<string>()
};

interface AffectsVersionStateRecord extends TypedRecord<AffectsVersionStateRecord>, AffectsVersionState {
}

const STATE_FACTORY = makeTypedFactory<AffectsVersionState, AffectsVersionStateRecord>(DEFAULT_STATE);
export const initialAffectsVersionState: AffectsVersionState = STATE_FACTORY(DEFAULT_STATE);

export class AffectsVersionUtil {
  static withMutations(s: AffectsVersionState, mutate: (mutable: AffectsVersionState) => any): AffectsVersionState {
    return (<AffectsVersionStateRecord>s).withMutations(mutable => {
      mutate(mutable);
    });
  }
}

