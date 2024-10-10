import type { ReactNode } from "react";

import { useSelector } from "metabase/lib/redux";
import { getLandingPageIllustration } from "metabase/selectors/whitelabel";

import {
  LayoutIllustration,
  LayoutRoot,
} from "./HomeLayout.styled";

interface HomeLayoutProps {
  hasMetabot: boolean;
  children?: ReactNode;
}

export const HomeLayout = ({
}: HomeLayoutProps): JSX.Element => {

  const landingPageIllustration = useSelector(getLandingPageIllustration);

  return (
    <LayoutRoot data-testid="home-page">
      {landingPageIllustration && (
        <LayoutIllustration
          data-testid="landing-page-illustration"
          backgroundImageSrc={landingPageIllustration.src}
          isDefault={landingPageIllustration.isDefault}
        />
      )}

    </LayoutRoot>
  );
};
