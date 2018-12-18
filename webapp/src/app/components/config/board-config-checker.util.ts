import {IssueQlUtil} from '../../common/parsers/issue-ql/issue-ql.util';
import * as issueQlParser from '../../common/parsers/issue-ql/pegjs/issue-ql.generated';

export class BoardConfigCheckerUtil {
  constructor(private _config: string) {

  }

  check(): string {
    const obj: Object = this.checkIsJson();
    if (!obj) {
      return 'Contents must be valid json';
    }
    return this.checkManualSwimlanesIssueQl(obj);
  }

  private checkIsJson(): Object {
    try {
      return JSON.parse(this._config);
    } catch (e) {
      return null;
    }
  }


  private checkManualSwimlanesIssueQl(boardConfig: Object): string {
    const cfg: any = boardConfig['config'];
    if (!cfg) {
      // Proper validation happens on server
      return null;
    }
    const mslConfig = cfg['manual-swimlanes'];
    if (mslConfig) {
      if (!Array.isArray(mslConfig)) {
        // Proper validation happens on server
        return null;
      }
      for (const msl of mslConfig) {
        const entries: any = msl['entries'];
        if (!Array.isArray(entries)) {
          // Proper validation happens on server
          return null;
        }
        for (const entry of entries) {
          let iql = entry['issue-ql'];
          if (!iql) {
            // Proper validation happens on server
            return null;
          }
          iql = iql.trim();
          let error: issueQlParser.SyntaxError;
          if (iql.length > 0) {
            error = IssueQlUtil.validateIssueQl(iql);
            if (error) {
              return `"Invalid Issue QL: "${iql}". The parser error is: ${error}"`;
            }
          }
        }
      }
    }
  }
}

