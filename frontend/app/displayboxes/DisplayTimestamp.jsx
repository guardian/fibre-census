import DisplayBox from "./DisplayBox.jsx";
import TimestampFormatter from "../common/TimestampFormatter.jsx";

class DisplayTimestamp extends DisplayBox {
    renderBodyContent() {
        return <TimestampFormatter relative={true} value={this.props.entry}/>
    }
}

export default DisplayTimestamp;