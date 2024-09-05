/* eslint "react/prop-types": "warn" */
import cx from "classnames";
import PropTypes from "prop-types";
import { Component } from "react";
import { connect } from "react-redux";

import SidebarLayout from "metabase/components/SidebarLayout";
import CS from "metabase/css/core/index.css";
import * as metadataActions from "metabase/redux/metadata";
import DatabaseList from "metabase/reference/databases/DatabaseList";
import BaseSidebar from "metabase/reference/guide/BaseSidebar";
import * as actions from "metabase/reference/reference";
import Databases from "metabase/entities/databases";
import {getDatabaseId, getIsEditing, getUser} from "../selectors";

const mapStateToProps = (state, props) => ({
  databaseId: getDatabaseId(state, props),
  isEditing: getIsEditing(state, props),
  user: getUser(state),
});

const mapDispatchToProps = {
  ...metadataActions,
  ...actions,
};

class DatabaseListContainer extends Component {
  static propTypes = {
    params: PropTypes.object.isRequired,
    databaseId: PropTypes.number.isRequired,
    location: PropTypes.object.isRequired,
    isEditing: PropTypes.bool,
    user: PropTypes.object,
  };

  async fetchContainerData() {
    //await actions.wrappedFetchDatabases(this.props);
    const user = this.props.user;
    Databases.actions.fetchList(
      user.is_superuser || !user.settings || !user.settings.db_id
        ? { include: "tables" }
        : { include: "tables", id: user.settings.db_id },
    );
  }

  UNSAFE_componentWillMount() {
    this.fetchContainerData();
  }

  UNSAFE_componentWillReceiveProps(newProps) {
    if (this.props.location.pathname === newProps.location.pathname) {
      return;
    }

    actions.clearState(newProps);
  }

  render() {
    const { isEditing } = this.props;

    return (
      <SidebarLayout
        className={cx(CS.flexFull, CS.relative)}
        style={isEditing ? { paddingTop: "43px" } : {}}
        sidebar={<BaseSidebar />}
      >
        <DatabaseList {...this.props} />
      </SidebarLayout>
    );
  }
}

export default connect(
  mapStateToProps,
  mapDispatchToProps,
)(DatabaseListContainer);
