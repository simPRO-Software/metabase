import { t } from "ttag";

import { useDatabaseListQuery } from "metabase/common/hooks";
import { getHasNativeWrite } from "metabase/selectors/data";
import {useSelector} from "metabase/lib/redux";
import {getUser} from "metabase/selectors/user";

export const NativeQueryLabel = () => {
  const user = useSelector(state => getUser(state));
  const { data: databases = [] } = useDatabaseListQuery({query: {id: user?.settings.db_id}});

  const hasNativeWrite = getHasNativeWrite(databases);

  const filterLabel = hasNativeWrite ? t`native` : `SQL`;

  return `Search the contents of ${filterLabel} queries`;
};
