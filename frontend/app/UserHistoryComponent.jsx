import React from 'react';
import PropTypes from 'prop-types';
import axios from 'axios';
import ReactTooltip from 'react-tooltip';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import TimestampFormatter from "./common/TimestampFormatter.jsx";

class UserHistoryComponent extends React.Component {
    static propTypes = {
        hostname: PropTypes.string.isRequired,
        limit: PropTypes.number
    };

    static durationExtractor = new RegExp('\w{2}(\d+)H(\d+)M');

    constructor(props){
        super(props);
        this.state = {
            loading: false,
            lastError: null,
            records: []
        };
    }

    componentWillMount(){
        const limit = this.props.limit ? this.props.limit : 1;
        const hostParts = this.props.hostname.split(".");

        this.setState({loading: true, lastError: null},
            ()=>axios.get("/api/logins/" + hostParts[0] + "?limit=" + limit)
                .then(response=>{
                    this.setState({loading: false, lastError: null, records: response.data.entries},()=>ReactTooltip.rebuild())
                })
                .catch(err=>{
                    this.setState({loading: false, lastError: err}, ()=>ReactTooltip.rebuild())
                })
        )
    }

    details(entry){
        return "On " + entry.location + " at " + entry.loginTime + " for " + this.niceifyDuration(entry.duration)
    }

    niceifyDuration(durationString){
        try {
            const values = durationString.match(UserHistoryComponent.durationExtractor);
            if (values) return values[1] + " hours, " + values[2] + " minutes";
        }catch (err){
            console.error(err);
            return durationString;
        }
    }

    render() {
        return <ul className="addressList">
            {
                this.state.records.map(entry=><li key={entry.loginTime} data-tip={this.details(entry)}><FontAwesomeIcon icon="user-alt"/>{entry.username} – <TimestampFormatter relative={false} value={entry.loginTime}/></li>)
            }
        </ul>
    }
}

export default UserHistoryComponent;
