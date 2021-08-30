import React, { PureComponent } from "react";
import Logo from "../default/Logo";
import Navigation from "../default/Navigation";
import { Default, Mobile } from "../MediaQuery";
import "../../css/header.css";
class Header extends PureComponent {
  render() {
    return (
      <React.Fragment>
        <Default>
          <div className="header-wrap">
            <Logo logoHeight={36} />
            <Navigation />
          </div>
        </Default>
        <Mobile>
          <div className="header-wrap">
            <Navigation />
            <Logo logoHeight={36} />
          </div>
        </Mobile>
      </React.Fragment>
    );
  }
}

export default Header;
