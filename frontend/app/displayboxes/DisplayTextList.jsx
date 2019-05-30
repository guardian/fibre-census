import DisplayBox from "./DisplayBox.jsx";

class DisplayTextList extends DisplayBox {
    renderBodyContent() {
        return this.props.listData ?
            <ul className="content-list">
                {this.props.listData.map(entry=><li key={entry}>{entry}</li>)}
            </ul> :
            <i>Not present</i>
    }
}

export default DisplayTextList;