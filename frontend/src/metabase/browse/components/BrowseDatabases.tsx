import { t } from "ttag";

import NoResults from "assets/img/no_results.svg";
import { useListDatabasesQuery } from "metabase/api";
import LoadingAndErrorWrapper from "metabase/components/LoadingAndErrorWrapper";
import CS from "metabase/css/core/index.css";
import { color } from "metabase/lib/colors";
import * as Urls from "metabase/lib/urls";
import { Box, Icon, Title } from "metabase/ui";

import {
  BrowseContainer,
  BrowseMain,
  BrowseSection,
  CenteredEmptyState,
} from "./BrowseContainer.styled";
import { BrowseDataHeader } from "./BrowseDataHeader";
import {
  DatabaseCard,
  DatabaseCardLink,
  DatabaseGrid,
} from "./BrowseDatabases.styled";
import {connect} from "react-redux";
import type {DatabaseId, User} from "metabase-types/api";
import type {NotebookDataPickerValueItem, TablePickerValue} from "metabase/common/components/DataPicker";
import type {State} from "metabase-types/store";
import {getUser} from "metabase/selectors/user";

interface Props {
  user: User | null
}

const mapStateToProps = (state: State) => ({
  user: getUser(state),
});

function BrowseDatabases ({ user }: Props) {
  const { data, isLoading, error } = useListDatabasesQuery(!user?.is_superuser ? { id: user?.settings.db_id } : {});
  const databases = data?.data;
  if (error) {
    return <LoadingAndErrorWrapper error />;
  }

  if (!databases && isLoading) {
    return <LoadingAndErrorWrapper loading />;
  }

  if (!databases?.length) {
    return (
      <CenteredEmptyState
        title={<Box mb=".5rem">{t`No databases here yet`}</Box>}
        illustrationElement={
          <Box mb=".5rem">
            <img src={NoResults} />
          </Box>
        }
      />
    );
  }

  return (
    <BrowseContainer>
      <BrowseDataHeader />
      <BrowseMain>
        <BrowseSection>
          <DatabaseGrid data-testid="database-browser">
            {databases.map(database => (
              <div key={database.id}>
                <DatabaseCardLink to={Urls.browseDatabase(database)}>
                  <DatabaseCard>
                    <Icon
                      name="database"
                      color={color("accent2")}
                      className={CS.mb3}
                      size={32}
                    />
                    <Title order={2} size="1rem" lh="1rem" color="inherit">
                      {database.name}
                    </Title>
                  </DatabaseCard>
                </DatabaseCardLink>
              </div>
            ))}
          </DatabaseGrid>
        </BrowseSection>
      </BrowseMain>
    </BrowseContainer>
  );
};

export default connect(mapStateToProps)(BrowseDatabases);
