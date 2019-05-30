import DisplayBox from "./DisplayBox.jsx";
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

class DisplayTextList extends DisplayBox {
    renderBodyContent() {
        return this.props.listData ?
            <ul className="content-list">
                {this.props.listData.map((entry,idx)=>
                    <li key={idx}>
                        {this.props.bulletIcon ? <FontAwesomeIcon icon={this.props.bulletIcon} className="list-bullet-icon"/> : "" }
                        {entry}
                    </li>)}
            </ul> :
            <i>Not present</i>
    }
}

export default DisplayTextList;