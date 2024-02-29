import ValidationComponent from "./ValidationComponent.jsx";

class ValidateFibreDrivers extends ValidationComponent {
    performValidation() {
        if(!this.props.listData){
            this.setState({tooltip: "No fibre drivers detected"});
            return "problem";
        }

        if(this.props.listData.length<1){
            this.setState({tooltip: "No fibre drivers detected"});
            return "problem";
        }

        const loadedDrivers = this.props.listData.filter(entry=>entry.loaded);
        if(loadedDrivers.length<1){
            this.setState({tooltip: "No drivers loaded. Test Atto connection?"});
            return "problem";
        }
        return "normal";
    }
}

export default ValidateFibreDrivers;