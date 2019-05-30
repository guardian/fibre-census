import DisplayBox from "./DisplayBox.jsx";

class DisplayMdcPing extends DisplayBox {
    renderBodyContent() {
        return this.props.listData ? <ul className="content-list">
            {
                this.props.listData.map(entry=>    //entry is a JSON version of models.MdcPing
                    <li key={entry.ipAddress}>
                        {entry.ipAddress}
                        <ul className="content-list-indent1">
                            <li>Packet loss: {entry.packetLoss}%</li>
                            <li>Pinged: {entry.visible}</li>
                        </ul>
                    </li>
                )
            }
        </ul> : <i>No ping data</i>
    }
}

export default DisplayMdcPing;