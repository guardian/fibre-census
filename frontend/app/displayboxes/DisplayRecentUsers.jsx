import DisplayBox from "./DisplayBox.jsx";
import UserHistoryComponent from "../UserHistoryComponent.jsx";

class DisplayRecentUsers extends DisplayBox {
    renderBodyContent() {
        return <UserHistoryComponent hostname={this.props.entry.hostName} limit={5}/>
    }
}

export default DisplayRecentUsers;