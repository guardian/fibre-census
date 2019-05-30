import React from 'react';
import PropTypes from 'prop-types';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import ReactTooltip from 'react-tooltip';

/**
 * unimportant - circle-minus
 * warning      - exclamation-triangle
 * info         - exclamation-circle
 * normal       - nothing
 */
class ValidationComponent extends React.Component {
    static propTypes = {
        stringData: PropTypes.string,
        listData: PropTypes.array,
        objectData: PropTypes.object
    };

    constructor(props){
        super(props);

        this.state = {
            status: "unimportant",
            tooltip: ""
        }
    }

    /**
     * override this function to perform validation of specific data.
     * it should return one of the status strings listed at the top of this module
     * @returns {string}
     */
    performValidation(){
        return "unimportant";
    }

    componentDidUpdate(oldProps, oldState){
        if(oldProps!==this.props) this.setState({status: this.performValidation()});
    }

    componentWillMount() {
        this.setState({status: this.performValidation()}, ()=>ReactTooltip.rebuild());  //need to call rebuild on tooltip if rendering from subcomponent
    }

    /**
     * returns a FontAwesome icon depending on the status
     */
    iconForStatus(){
        if(this.state.status==="unimportant"){
            return <FontAwesomeIcon icon="minus-circle" style={{color: "grey"}}/>
        } else if(this.state.status==="warning"){
            return <FontAwesomeIcon icon="exclamation-triangle" style={{color: "red"}}/>
        } else if(this.state.status==="info"){
            return <FontAwesomeIcon icon="exclamation-circle" style={{color: "yellow"}}/>
        } else {
            return <span/>
        }
    }

    render(){
        return <span data-tip={this.state.tooltip} className="validation-icon">{this.iconForStatus()}</span>
    }
}

export default ValidationComponent;