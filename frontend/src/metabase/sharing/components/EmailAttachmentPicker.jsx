/* eslint-disable react/prop-types */
import React, { Component } from "react";
import PropTypes from "prop-types";
import _ from "underscore";
import { t } from "ttag";

import { SegmentedControl } from "metabase/components/SegmentedControl";
import CheckBox from "metabase/core/components/CheckBox";
import Label from "metabase/components/type/Label";
import StackedCheckBox from "metabase/components/StackedCheckBox";
import Toggle from "metabase/core/components/Toggle";

export default class EmailAttachmentPicker extends Component {
  DEFAULT_ATTACHMENT_TYPE = "csv";

  state = {
    isEnabled: false,
    selectedAttachmentType: this.DEFAULT_ATTACHMENT_TYPE,
    selectedCardIds: new Set(),
  };

  static propTypes = {
    pulse: PropTypes.object.isRequired,
    setPulse: PropTypes.func.isRequired,
    cards: PropTypes.array.isRequired,
  };

  componentDidMount() {
    this.setState(this.calculateStateFromCards());
  }

  componentDidUpdate(prevProps) {
    const newState = this.calculateStateFromCards();

    // keep the toggle on, unless the cards were loaded again from a pulse
    const isSameCards =
      this.props.cards === prevProps.cards ||
      this.props.cards === this.updatedCards;
    if (isSameCards) {
      newState.isEnabled = newState.isEnabled || this.state.isEnabled;
    }
    if (newState.selectedCardIds.size === 0) {
      newState.selectedAttachmentType = this.state.selectedAttachmentType;
    }

    if (!this.shouldUpdateState(newState, this.state)) {
      this.setState(newState);
    }
  }

  calculateStateFromCards() {
    const { cards } = this.props;
    const selectedCards = cards.filter(card => {
      return card.include_csv || card.include_xls;
    });

    return {
      isEnabled: selectedCards.length > 0,
      selectedAttachmentType:
        this.attachmentTypeFor(selectedCards) || this.DEFAULT_ATTACHMENT_TYPE,
      selectedCardIds: new Set(selectedCards.map(card => card.id)),
    };
  }

  shouldUpdateState(newState, currentState) {
    return (
      (currentState.isEnabled || !newState.isEnabled) &&
      newState.selectedAttachmentType === currentState.selectedAttachmentType &&
      // underscore sees any two Sets as equal, so compare the card IDs
      _.isEqual(
        [...newState.selectedCardIds].sort(),
        [...currentState.selectedCardIds].sort(),
      )
    );
  }

  /*
   * Reaches into the parent component (via setPulse) to update its pulsecard's include_{csv,xls} values
   * based on this component's state.
   */
  updatePulseCards(attachmentType, selectedCardIds) {
    const { pulse, setPulse } = this.props;
    const isXls = attachmentType === "xls",
      isCsv = attachmentType === "csv";

    this.setState({ selectedAttachmentType: attachmentType });

    // copy the cards, they can be shared with the saved pulse
    this.updatedCards = pulse.cards.map(card => ({
      ...card,
      include_csv: selectedCardIds.has(card.id) && isCsv,
      include_xls: selectedCardIds.has(card.id) && isXls,
    }));
    setPulse({ ...pulse, cards: this.updatedCards });
  }

  cardIds() {
    return new Set(this.props.cards.map(card => card.id));
  }

  cardIdsToCards(cardIds) {
    const { pulse } = this.props;

    return pulse.cards.filter(card => cardIds.has(card.id));
  }

  attachmentTypeFor(cards) {
    if (cards.some(c => c.include_xls)) {
      return "xls";
    } else if (cards.some(c => c.include_csv)) {
      return "csv";
    } else {
      return null;
    }
  }

  /*
   * Called when the attachment type toggle (csv/xls) is clicked
   */
  setAttachmentType = newAttachmentType => {
    this.updatePulseCards(newAttachmentType, this.state.selectedCardIds);
  };

  /*
   * Called when attachments are enabled/disabled at all
   */
  toggleAttach = includeAttachment => {
    if (!includeAttachment) {
      this.disableAllCards();
    }

    this.setState({ isEnabled: includeAttachment });
  };

  /*
   * Called on card selection
   */
  onToggleCard(card) {
    const id = card.id;
    const attachmentType =
      this.attachmentTypeFor(this.cardIdsToCards(this.state.selectedCardIds)) ||
      this.state.selectedAttachmentType;
    const selectedCardIds = new Set(this.state.selectedCardIds);

    if (selectedCardIds.has(id)) {
      selectedCardIds.delete(id);
    } else {
      selectedCardIds.add(id);
    }

    this.updatePulseCards(attachmentType, selectedCardIds);
    this.setState({ selectedCardIds });
  }

  /*
   * Called when (de)select-all checkbox is clicked
   */
  onToggleAll = () => {
    const { cards } = this.props;
    const { selectedAttachmentType, selectedCardIds } = this.state;

    const attachmentType =
      this.attachmentTypeFor(this.cardIdsToCards(selectedCardIds)) ||
      selectedAttachmentType;
    let newSelectedCardIds = this.cardIds();
    if (this.areAllSelected(cards, selectedCardIds)) {
      newSelectedCardIds = new Set();
    }

    this.updatePulseCards(attachmentType, newSelectedCardIds);
    this.setState({ selectedCardIds: newSelectedCardIds });
  };

  disableAllCards() {
    const selectedCardIds = new Set();
    this.updatePulseCards(this.state.selectedAttachmentType, selectedCardIds);
    this.setState({ selectedCardIds });
  }

  areAllSelected(allCards, selectedCardSet) {
    return allCards.length === selectedCardSet.size;
  }

  areOnlySomeSelected(allCards, selectedCardSet) {
    return 0 < selectedCardSet.size && selectedCardSet.size < allCards.length;
  }

  render() {
    const { cards } = this.props;
    const { isEnabled, selectedAttachmentType, selectedCardIds } = this.state;

    return (
      <div>
        <Toggle
          aria-label={t`Attach results`}
          value={isEnabled}
          onChange={this.toggleAttach}
        />

        {isEnabled && (
          <div>
            <div className="my1 flex justify-between">
              <Label className="pt1">{t`File format`}</Label>
              <SegmentedControl
                options={[
                  { name: ".csv", value: "csv" },
                  { name: ".xlsx", value: "xls" },
                ]}
                onChange={this.setAttachmentType}
                value={selectedAttachmentType}
                fullWidth
              />
            </div>
            <div className="text-bold pt1 pb2 flex justify-between align-center">
              <ul className="full">
                <li className="mb2 pb1 flex align-center cursor-pointer border-bottom">
                  <StackedCheckBox
                    label={t`Questions to attach`}
                    checked={this.areAllSelected(cards, selectedCardIds)}
                    indeterminate={this.areOnlySomeSelected(
                      cards,
                      selectedCardIds,
                    )}
                    onChange={this.onToggleAll}
                  />
                </li>
                {cards.map(card => (
                  <li
                    key={card.id}
                    className="pb2 flex align-center cursor-pointer"
                  >
                    <CheckBox
                      checked={selectedCardIds.has(card.id)}
                      label={card.name}
                      onChange={() => {
                        this.onToggleCard(card);
                      }}
                      className="mr1"
                    />
                  </li>
                ))}
              </ul>
            </div>
          </div>
        )}
      </div>
    );
  }
}
