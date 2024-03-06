import { useMemo } from "react";
import { t } from "ttag";

import ErrorBoundary from "metabase/ErrorBoundary";
import { FilterPicker } from "metabase/querying";
import * as Lib from "metabase-lib";

import type { NotebookStepUiComponentProps } from "../../types";
import { ClauseStep } from "../ClauseStep";

export function FilterStep({
  query,
  step,
  color,
  isLastOpened,
  readOnly,
  updateQuery,
}: NotebookStepUiComponentProps) {
  const { stageIndex } = step;

  const filters = useMemo(
    () => Lib.filters(query, stageIndex),
    [query, stageIndex],
  );

  const handleAddFilter = (
    filter: Lib.ExpressionClause | Lib.FilterClause | Lib.SegmentMetadata,
  ) => {
    const nextQuery = Lib.filter(query, stageIndex, filter);
    updateQuery(nextQuery);
  };

  const handleUpdateFilter = (
    targetFilter: Lib.FilterClause,
    nextFilter: Lib.ExpressionClause | Lib.FilterClause | Lib.SegmentMetadata,
  ) => {
    const nextQuery = Lib.replaceClause(
      query,
      stageIndex,
      targetFilter,
      nextFilter,
    );
    updateQuery(nextQuery);
  };

  const handleRemoveFilter = (filter: Lib.FilterClause) => {
    const nextQuery = Lib.removeClause(query, stageIndex, filter);
    updateQuery(nextQuery);
  };

  const renderFilterName = (filter: Lib.FilterClause) =>
    Lib.displayInfo(query, stageIndex, filter).longDisplayName;

  return (
    <ErrorBoundary>
      <ClauseStep
        items={filters}
        initialAddText={t`Add filters to narrow your answer`}
        readOnly={readOnly}
        color={color}
        isLastOpened={isLastOpened}
        renderName={renderFilterName}
        renderPopover={({ item: filter, index, onClose }) => (
          <FilterPopover
            query={query}
            stageIndex={stageIndex}
            filter={filter}
            filterIndex={index}
            onAddFilter={handleAddFilter}
            onUpdateFilter={handleUpdateFilter}
            onClose={onClose}
          />
        )}
        onRemove={handleRemoveFilter}
      />
    </ErrorBoundary>
  );
}

interface FilterPopoverProps {
  query: Lib.Query;
  stageIndex: number;
  filter?: Lib.FilterClause;
  filterIndex?: number;
  onAddFilter: (
    filter: Lib.ExpressionClause | Lib.FilterClause | Lib.SegmentMetadata,
  ) => void;
  onUpdateFilter: (
    targetFilter: Lib.FilterClause,
    nextFilter: Lib.ExpressionClause | Lib.FilterClause | Lib.SegmentMetadata,
  ) => void;
  onClose?: () => void;
}

function FilterPopover({
  query,
  stageIndex,
  filter,
  filterIndex,
  onAddFilter,
  onUpdateFilter,
  onClose,
}: FilterPopoverProps) {
  return (
    <FilterPicker
      query={query}
      stageIndex={stageIndex}
      filter={filter}
      filterIndex={filterIndex}
      onSelect={newFilter => {
        if (filter) {
          onUpdateFilter(filter, newFilter);
        } else {
          onAddFilter(newFilter);
        }
      }}
      onClose={onClose}
    />
  );
}
