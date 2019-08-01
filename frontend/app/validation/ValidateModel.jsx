import ValidationComponent from "./ValidationComponent.jsx";

class ValidateModel extends ValidationComponent {
    performValidation() {
        if(this.props.stringData==="Mac Pro"){
            this.setState({"tooltip": ""});
            return "normal";
        } else {
            this.setState({"tooltip": "Not expecting this to be connected to the SAN"});
            return "unimportant";
        }
    }
}

export default ValidateModel;