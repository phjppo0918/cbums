import React, { PureComponent } from "react";
import style from "../../css/default/default.module.css";

class Line extends PureComponent {
  render() {
    return (
      <React.Fragment>
        <div className={style.contour}></div>
      </React.Fragment>
    );
  }
}

export default Line;
