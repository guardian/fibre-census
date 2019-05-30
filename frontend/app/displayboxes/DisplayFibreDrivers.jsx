import DisplayBox from "./DisplayBox.jsx";
import FibreDriversComponent from "../FibreDriversComponent.jsx";

class DisplayFibreDrivers extends DisplayBox {
    renderBodyContent() {
        return <FibreDriversComponent entryList={this.props.listData} showDetails={this.props.showDetails}/>
    }
}

export default DisplayFibreDrivers;