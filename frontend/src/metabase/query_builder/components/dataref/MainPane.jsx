/* eslint "react/prop-types": "warn" */
import cx from "classnames";
import { connect } from "react-redux";
import PropTypes from "prop-types";
import { t } from "ttag";
import _ from "underscore";
import { getUser } from "metabase/selectors/user";
import CS from "metabase/css/core/index.css";
import Databases from "metabase/entities/databases";
import SidebarContent from "metabase/query_builder/components/SidebarContent";

import {
  NodeListItemIcon,
  NodeListItemLink,
  NodeListItemName,
} from "./NodeList.styled";
import { PaneContent } from "./Pane.styled";

const MainPane = ({ databases, onClose, onItemClick }) => (
  <SidebarContent title={t`Data Reference`} onClose={onClose}>
    <PaneContent>
      <p className={cx(CS.mt2, CS.mb3, CS.textSpaced)}>
        {t`Browse the contents of your databases, tables, and columns. Pick a database to get started.`}
      </p>
      <ul>
        {databases &&
          databases.map(database => (
            <li key={database.id}>
              <NodeListItemLink
                onClick={() => onItemClick("database", database)}
              >
                <NodeListItemIcon name="database" />
                <NodeListItemName>{database.name}</NodeListItemName>
              </NodeListItemLink>
            </li>
          ))}
      </ul>
    </PaneContent>
  </SidebarContent>
);

MainPane.propTypes = {
  databases: PropTypes.array,
  onClose: PropTypes.func.isRequired,
  onItemClick: PropTypes.func.isRequired,
};

export default _.compose(
  Databases.loadList({
    query: (state, ownProps) => {
      const user = getUser(state);
      return user.is_superuser || !user.settings || !user.settings.db_id
        ? {}
        : { id: user.settings.db_id };
    },
  }),
  connect((state, ownProps) => ({
    user: getUser(state),
  })),
)(MainPane);

