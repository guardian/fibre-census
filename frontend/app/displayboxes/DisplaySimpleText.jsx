import DisplayBox from "./DisplayBox.jsx";

class DisplaySimpleText extends DisplayBox {
    renderBodyContent() {
        return <ul className="content-list">
            <li>{this.props.entry}</li>
        </ul>
    }
}

export default DisplaySimpleText;