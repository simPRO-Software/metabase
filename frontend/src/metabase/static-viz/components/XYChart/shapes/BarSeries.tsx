import React, { Fragment } from "react";
import { Bar } from "@visx/shape";
import { Group } from "@visx/group";
import { scaleBand } from "@visx/scale";
import { PositionScale } from "@visx/shape/lib/types";
import { getY } from "metabase/static-viz/components/XYChart/utils";

import type {
  DatumAccessor,
  HydratedSeries,
} from "metabase/static-viz/components/XYChart/types";

interface BarSeriesProps {
  series: HydratedSeries[];
  yScaleLeft: PositionScale | null;
  yScaleRight: PositionScale | null;
  xAccessor: DatumAccessor;
  bandwidth: number;
  areStacked?: boolean;
}

export const BarSeries = ({
  series,
  yScaleLeft,
  yScaleRight,
  xAccessor,
  bandwidth,
  areStacked,
}: BarSeriesProps) => {
  if (areStacked) {
    // Stacked bars (BI-86) fill the whole band. Each segment goes from the sum of
    // the series below it (stacked datum [x, y1 + y, y1]) up to that sum plus its value.
    return (
      <Group>
        {series.map((series, seriesIndex) => {
          const yScale =
            series.yAxisPosition === "left" ? yScaleLeft : yScaleRight;

          if (!yScale || !series.stackedData) {
            return null;
          }

          return (
            <Fragment key={seriesIndex}>
              {series.stackedData.map(([x, top, bottom], index) => {
                const yTop = yScale(top) ?? 0;
                const yBottom = yScale(bottom) ?? 0;

                return (
                  <Bar
                    key={index}
                    fill={series.color}
                    width={bandwidth}
                    height={Math.abs(yBottom - yTop)}
                    x={xAccessor([x, top])}
                    y={Math.min(yTop, yBottom)}
                  />
                );
              })}
            </Fragment>
          );
        })}
      </Group>
    );
  }

  const innerBarScaleDomain = series.map((_, index) => index);

  const innerBarScale = scaleBand({
    domain: innerBarScaleDomain,
    range: [0, bandwidth],
  });

  return (
    <Group>
      {series.map((series, seriesIndex) => {
        const yScale =
          series.yAxisPosition === "left" ? yScaleLeft : yScaleRight;

        if (!yScale) {
          return null;
        }

        return (
          <Fragment key={seriesIndex}>
            {series.data.map((datum, index) => {
              const groupX = xAccessor(datum);
              const innerX = innerBarScale(seriesIndex) ?? 0;

              const x = groupX + innerX;
              const width = innerBarScale.bandwidth();

              const yZero = yScale(0) ?? 0;
              const yValue = yScale(getY(datum)) ?? 0;
              const y = Math.min(yValue, yZero);
              const height = Math.abs(yValue - yZero);

              return (
                <Bar
                  key={index}
                  fill={series.color}
                  width={width}
                  height={height}
                  x={x}
                  y={y}
                />
              );
            })}
          </Fragment>
        );
      })}
    </Group>
  );
};
