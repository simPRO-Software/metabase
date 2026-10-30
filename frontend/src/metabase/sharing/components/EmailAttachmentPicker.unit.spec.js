import React from "react";
import { render, fireEvent, screen } from "@testing-library/react";

import EmailAttachmentPicker from "./EmailAttachmentPicker";

describe("EmailAttachmentPicker", () => {
  describe("when instantiated without any cards with attachments", () => {
    let pulse;
    let setPulse;
    beforeEach(() => {
      pulse = createPulse();
      setPulse = jest.fn();
      render(<PickerWithPulse initialPulse={pulse} onSetPulse={setPulse} />);
    });

    it("should have a Toggle that is not toggled", () => {
      const toggle = screen.getByLabelText("Attach results");
      expect(toggle).toBeInTheDocument();
      expect(toggle).not.toBeChecked();
    });

    it("should have a clickable toggle that reveals attachment type and a checkbox per question", () => {
      expect(screen.queryByText("File format")).toBeNull();
      expect(screen.queryByText("Questions to attach")).toBeNull();
      expect(screen.queryByText("card1")).toBeNull();
      expect(screen.queryByText("card2")).toBeNull();

      const toggle = screen.getByLabelText("Attach results");
      fireEvent.click(toggle);

      const csvFormatInput = screen.getByLabelText(".csv");
      expect(csvFormatInput).toBeChecked();

      const toggleAllCheckbox = screen.getByLabelText("Questions to attach");
      expect(toggleAllCheckbox).not.toBeChecked();

      const card1Checkbox = screen.getByLabelText("card1");
      expect(card1Checkbox).not.toBeChecked();

      const card2Checkbox = screen.getByLabelText("card2");
      expect(card2Checkbox).not.toBeChecked();
    });
  });

  describe("when instantiated with cards with attachments", () => {
    let pulse;
    let setPulse;
    beforeEach(() => {
      pulse = createPulse();
      pulse.cards[0]["include_xls"] = true;
      setPulse = jest.fn();
      render(<PickerWithPulse initialPulse={pulse} onSetPulse={setPulse} />);
    });

    it("should have a toggled Toggle", () => {
      const toggle = screen.getByLabelText("Attach results");
      expect(toggle).toBeInTheDocument();
      expect(toggle).toHaveProperty("checked", true);
    });

    it("should have selected the xlsv format", () => {
      const csvFormatInput = screen.getByLabelText(".csv");
      expect(csvFormatInput).not.toBeChecked();
      const xlsxFormatInput = screen.getByLabelText(".xlsx");
      expect(xlsxFormatInput).toBeChecked();
    });

    it("should show a checked checkbox for the card with an attachment", () => {
      const toggleAllCheckbox = screen.getByLabelText("Questions to attach");
      expect(toggleAllCheckbox).not.toBeChecked();

      const card1Checkbox = screen.getByLabelText("card1");
      expect(card1Checkbox).toBeChecked();

      const card2Checkbox = screen.getByLabelText("card2");
      expect(card2Checkbox).not.toBeChecked();
    });

    it("should let you check or uncheck card checkboxes", () => {
      const card1Checkbox = screen.getByLabelText("card1");
      fireEvent.click(card1Checkbox);
      expect(card1Checkbox).not.toBeChecked();
      fireEvent.click(card1Checkbox);
      expect(card1Checkbox).toBeChecked();
    });

    it("should let you check all checkboxes", () => {
      const card2Checkbox = screen.getByLabelText("card2");
      fireEvent.click(card2Checkbox);
      expect(card2Checkbox).toBeChecked();
      expect(screen.getByLabelText("Questions to attach")).toBeChecked();
    });

    it("should let you check/uncheck all boxes via the `Questions to attach` toggle", () => {
      const toggleAllCheckbox = screen.getByLabelText("Questions to attach");
      const card1Checkbox = screen.getByLabelText("card1");
      const card2Checkbox = screen.getByLabelText("card2");

      fireEvent.click(toggleAllCheckbox);

      expect(screen.getByLabelText("Questions to attach")).toBeChecked();
      expect(card1Checkbox).toBeChecked();
      expect(card2Checkbox).toBeChecked();

      fireEvent.click(toggleAllCheckbox);

      expect(screen.getByLabelText("Questions to attach")).not.toBeChecked();
      expect(card1Checkbox).not.toBeChecked();
      expect(card2Checkbox).not.toBeChecked();
    });

    it("should uncheck all boxes if disabling attachments", () => {
      const toggle = screen.getByLabelText("Attach results");
      expect(screen.getByLabelText("card1")).toBeChecked();

      fireEvent.click(toggle);

      expect(screen.queryByText("File format")).toBeNull();
      expect(screen.queryByText("Questions to attach")).toBeNull();
      expect(screen.queryByText("card1")).toBeNull();
      expect(screen.queryByText("card2")).toBeNull();

      fireEvent.click(toggle);
      expect(screen.getByLabelText("card1")).not.toBeChecked();
    });

    it("should not change the cards of the pulse it was given", () => {
      fireEvent.click(screen.getByLabelText("card2"));

      expect(screen.getByLabelText("card2")).toBeChecked();
      expect(pulse.cards[1].include_xls).toBe(false);
    });
  });

  describe("when given another pulse", () => {
    it("should show the attachments of the new pulse", () => {
      const pulseWithAttachment = createPulse();
      pulseWithAttachment.cards[0]["include_csv"] = true;
      const pulseWithOtherAttachment = createPulse();
      pulseWithOtherAttachment.cards[1]["include_csv"] = true;

      const { rerender } = render(
        <EmailAttachmentPicker
          cards={pulseWithAttachment.cards}
          pulse={pulseWithAttachment}
          setPulse={jest.fn()}
        />,
      );
      expect(screen.getByLabelText("card1")).toBeChecked();
      expect(screen.getByLabelText("card2")).not.toBeChecked();

      rerender(
        <EmailAttachmentPicker
          cards={pulseWithOtherAttachment.cards}
          pulse={pulseWithOtherAttachment}
          setPulse={jest.fn()}
        />,
      );
      expect(screen.getByLabelText("card1")).not.toBeChecked();
      expect(screen.getByLabelText("card2")).toBeChecked();
    });

    it("should turn attachments off when the new pulse has none", () => {
      const pulseWithAttachment = createPulse();
      pulseWithAttachment.cards[0]["include_csv"] = true;
      const pulseWithoutAttachment = createPulse();

      const { rerender } = render(
        <EmailAttachmentPicker
          cards={pulseWithAttachment.cards}
          pulse={pulseWithAttachment}
          setPulse={jest.fn()}
        />,
      );
      expect(screen.getByLabelText("Attach results")).toBeChecked();

      rerender(
        <EmailAttachmentPicker
          cards={pulseWithoutAttachment.cards}
          pulse={pulseWithoutAttachment}
          setPulse={jest.fn()}
        />,
      );
      expect(screen.getByLabelText("Attach results")).not.toBeChecked();
    });

    it("should keep attachments on when other fields of the pulse change", () => {
      const pulse = createPulse();

      const { rerender } = render(
        <EmailAttachmentPicker
          cards={pulse.cards}
          pulse={pulse}
          setPulse={jest.fn()}
        />,
      );
      fireEvent.click(screen.getByLabelText("Attach results"));

      const renamedPulse = { ...pulse, name: "Renamed" };
      rerender(
        <EmailAttachmentPicker
          cards={renamedPulse.cards}
          pulse={renamedPulse}
          setPulse={jest.fn()}
        />,
      );
      expect(screen.getByLabelText("Attach results")).toBeChecked();
    });
  });
});

// keeps the pulse in state, as the sharing sidebar does
// eslint-disable-next-line react/prop-types
function PickerWithPulse({ initialPulse, onSetPulse }) {
  const [pulse, setPulse] = React.useState(initialPulse);

  return (
    <EmailAttachmentPicker
      cards={pulse.cards}
      pulse={pulse}
      setPulse={newPulse => {
        onSetPulse(newPulse);
        setPulse(newPulse);
      }}
    />
  );
}

function createPulse() {
  return {
    name: "Parameters",
    cards: [
      {
        id: 4,
        collection_id: null,
        description: null,
        display: "map",
        name: "card1",
        include_csv: false,
        include_xls: false,
        dashboard_card_id: 3,
        dashboard_id: 1,
        parameter_mappings: [],
      },
      {
        id: 6,
        collection_id: null,
        description: null,
        display: "scalar",
        name: "card2",
        include_csv: false,
        include_xls: false,
        dashboard_card_id: 4,
        dashboard_id: 1,
        parameter_mappings: [],
      },
    ],
    channels: [
      {
        channel_type: "email",
        enabled: true,
        recipients: [],
        details: {},
        schedule_type: "hourly",
        schedule_day: "mon",
        schedule_hour: 8,
        schedule_frame: "first",
      },
      {
        channel_type: "email",
        enabled: true,
        recipients: [],
        details: {},
        schedule_type: "hourly",
        schedule_day: "mon",
        schedule_hour: 8,
        schedule_frame: "first",
      },
    ],
    skip_if_empty: false,
    collection_id: null,
    parameters: [],
    dashboard_id: 1,
  };
}
