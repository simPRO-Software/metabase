import cx from "classnames";
import PropTypes from "prop-types";
import { Component } from "react";

import CS from "metabase/css/core/index.css";
import { PLUGIN_LOGO_ICON_COMPONENTS } from "metabase/plugins";

class DefaultLogoIcon extends Component {
  static defaultProps = {
    height: 32,
  };
  static propTypes = {
    width: PropTypes.number,
    height: PropTypes.number,
    dark: PropTypes.bool,
  };

  render() {
    const { dark, height, width } = this.props;
    return (
      <svg
        className={cx(
          "Icon",
          { [CS.textBrand]: !dark },
          { [CS.textWhite]: dark },
        )}
        viewBox="0 0 66 85"
        width={width}
        height={height}
        fill="currentcolor"
        data-testid="main-logo"
      >
        <g transform="matrix(0.6037736 0 0 0.6037736 0.3018868 -0)">
          <path
            d="M52.5096 0.469727C23.5196 0.469727 0.0195312 23.9697 0.0195312 52.9597C0.0195312 81.9497 23.5296 105.44 52.5096 105.44C81.4896 105.44 105 81.9397 105 52.9597C105 23.9797 81.4996 0.469727 52.5096 0.469727ZM82.8795 57.9497L78.9095 59.1797C73.6795 61.4897 69.8995 68.0297 70.5195 73.7197L71.4395 77.7697C71.7595 80.6997 70.4295 81.5397 69.0795 82.3197C67.7195 83.1097 66.3296 83.8297 63.9496 82.0897L60.8995 79.2697C56.2895 75.8897 48.7395 75.8897 44.1195 79.2697L41.0695 82.0897C38.7295 83.7997 37.3696 83.0897 36.0096 82.3597C34.6096 81.5997 33.2595 80.7497 33.5795 77.7697L34.4995 73.7197C35.1195 68.0297 31.3395 61.4897 26.1095 59.1797L22.1396 57.9497C19.4496 56.7597 19.3795 55.1997 19.3795 53.6297C19.3795 52.0597 19.4496 50.4997 22.1396 49.3097L26.1095 48.0797C31.3395 45.7697 35.1195 39.2297 34.4995 33.5397L33.5795 29.4897C33.2695 26.5597 34.5896 25.7197 35.9496 24.9397C37.2996 24.1497 38.6895 23.4297 41.0695 25.1697L44.1195 27.9897C48.7395 31.3697 56.2895 31.3697 60.8995 27.9897L63.9496 25.1697C66.3296 23.4297 67.7195 24.1497 69.0795 24.9397C70.4295 25.7197 71.7595 26.5597 71.4395 29.4897L70.5195 33.5397C69.8995 39.2297 73.6795 45.7697 78.9095 48.0797L82.8795 49.3097C85.5695 50.4997 85.6396 52.0597 85.6396 53.6297C85.6396 55.1997 85.5695 56.7597 82.8795 57.9497Z"
            fill="#002F87"
          />
        </g>
      </svg>
    );
  }
}

export default function LogoIcon(props) {
  const [Component = DefaultLogoIcon] = PLUGIN_LOGO_ICON_COMPONENTS;
  return <Component {...props} />;
}
