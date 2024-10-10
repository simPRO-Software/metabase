import { connect } from "react-redux";
import { push } from "react-router-redux";
import _ from "underscore";

import NewItemMenu from "metabase/components/NewItemMenu";
import Databases from "metabase/entities/databases";
import Search from "metabase/entities/search";
import { closeNavbar } from "metabase/redux/app";
import {
  getHasDataAccess,
  getHasDatabaseWithActionsEnabled,
  getHasDatabaseWithJsonEngine,
  getHasNativeWrite,
} from "metabase/selectors/data";
import { getUser } from "metabase/selectors/user";
import type Database from "metabase-lib/v1/metadata/Database";
import type { CollectionItem, User } from "metabase-types/api";
import type { State } from "metabase-types/store";

interface MenuDatabaseProps {
  databases?: Database[];
  models?: CollectionItem[];
  currentUser: User;
}

const mapStateToProps = (
  state: State,
  { databases = [], models = [] }: MenuDatabaseProps,
) => ({
  hasModels: models.length > 0,
  hasDataAccess: getHasDataAccess(databases),
  hasNativeWrite: getHasNativeWrite(databases),
  hasDatabaseWithJsonEngine: getHasDatabaseWithJsonEngine(databases),
  hasDatabaseWithActionsEnabled: getHasDatabaseWithActionsEnabled(databases),
  currentUser: getUser(state),
});

const mapDispatchToProps = {
  onCloseNavbar: closeNavbar,
  onChangeLocation: push,
};

// eslint-disable-next-line import/no-default-export -- deprecated usage
export default _.compose(
  Databases.loadList({
    query: (props: MenuDatabaseProps) => {
      const user = props.currentUser;
      //console.log('user', user);
      return !user ||
        user.is_superuser ||
        !user.settings ||
        !user.settings.db_id
        ? {}
        : { id: user.settings.db_id };
    },
    loadingAndErrorWrapper: false,
  }),
  Search.loadList({
    // Checking if there is at least one model,
    // so we can decide if "Action" option should be shown
    query: { models: ["dataset"], limit: 1 },
    loadingAndErrorWrapper: false,
    listName: "models",
  }),
  connect(mapStateToProps, mapDispatchToProps),
)(NewItemMenu);
