import React from "react";
import { Flex } from "grid-styled";
import { Link } from "react-router";

import Icon from "metabase/components/Icon";

import { entityObjectLoader } from "metabase/entities/containers/EntityObjectLoader";
import * as Urls from "metabase/lib/urls";

import cx from "classnames";

@entityObjectLoader({
  entityType: "collections",
  entityId: (state, props) => props.collectionId || "root",
  wrapped: true,
})
class CollectionBadge extends React.Component {
  render() {
    const { analyticsContext, object } = this.props;
    return (
      <Link
        to={Urls.collection(object.id)}
        className={cx("inline-block link")}
        data-metabase-event={`${analyticsContext};Collection Badge Click`}
      >
        <Flex align="center">
          <Icon name={object.getIcon()} mr={1} />
          <h4 style={{ fontWeight: 900 }}>
            {object.name}
          </h4>
        </Flex>
      </Link>
    );
  }
}

export default CollectionBadge;
