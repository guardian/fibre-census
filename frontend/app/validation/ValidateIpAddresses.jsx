import ValidationComponent from "./ValidationComponent.jsx";

class ValidateIpAddresses extends ValidationComponent {
    performValidation() {
        if(!this.props.listData){
            this.setState({"tooltip": "No network connections detected"});
            return "warning";
        }

        if(this.props.listData.length<2){
            this.setState({"tooltip": "No metadata network"});
            return "warning";
        }
        return "normal";
    }
}

export default ValidateIpAddresses;