import DisplayBox from "./DisplayBox.jsx";
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

class DisplayMdcPing extends DisplayBox {
    renderBool(value){
        return value ?
            <span>Yes<FontAwesomeIcon icon="check" style={{color: "green", marginLeft: "1em"}}/></span> :
            <span>No<FontAwesomeIcon icon="minus-circle" style={{color: "yellow", marginLeft: "1em"}}/></span>
    }

    renderBodyContent() {
        return this.props.listData ? <ul className="content-list">
            {
                this.props.listData.map(entry=>    //entry is a JSON version of models.MdcPing
                    <li key={entry.ipAddress}>
                        {entry.ipAddress}
                        <ul className="content-list-indent1">
                            <li>Packet loss: {entry.packetloss}%</li>
                            <li>Pinged: {this.renderBool(entry.visible)}</li>
                        </ul>
                    </li>
                )
            }
        </ul> : <i>No ping data</i>
    }
}

export default DisplayMdcPing;